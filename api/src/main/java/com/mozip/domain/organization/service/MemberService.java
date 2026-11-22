package com.mozip.domain.organization.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.RepresentativeTransfer;
import com.mozip.domain.organization.entity.TransferStatus;
import com.mozip.domain.organization.exception.MemberErrorCode;
import com.mozip.domain.organization.exception.MemberReason;
import com.mozip.domain.organization.repository.InviteLinkRepository;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.RepresentativeTransferRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 운영진 관리 (목록, 내보내기)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final InviteLinkRepository inviteLinkRepository;
	private final OrganizationService organizationService;
	private final RepresentativeTransferRepository representativeTransferRepository;

	// 그 단체 운영진만 조회 가능, 없으면 ORG_404이고 소속이 아니면 COMMON_403
	public List<Member> getMembers(Long userId, Long organizationId) {
		organizationService.getOrganization(organizationId);
		if (!memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId)) {
			throw forbidden(organizationId, userId);
		}
		return memberRepository.findAllActiveWithUserAndTeamByOrganizationId(organizationId);
	}

	// 대표자만 내보낼 수 있고 대표자 본인은 내보낼 수 없음
	// 행은 남기고 leftAt을 찍으며, 남은 소속이 없으면 그 회원의 lastRole을 APPLICANT로
	@Transactional
	public void removeMember(Long userId, Long memberId) {
		Long organizationId = memberRepository.findOrganizationIdById(memberId)
			.orElseThrow(() -> memberNotFound(memberId));
		// 단체 행을 먼저 잠그고 운영진을 읽음 (대표자 인수 수락과 차례로 처리)
		organizationService.getOrganizationForUpdate(organizationId);
		Member member = memberRepository.findActiveWithOrganizationAndUserById(memberId)
			.orElseThrow(() -> memberNotFound(memberId));
		if (!memberRepository.existsByOrganizationIdAndUserIdAndRoleAndLeftAtIsNull(organizationId, userId,
			MemberRole.REPRESENTATIVE)) {
			throw forbidden(organizationId, userId);
		}
		if (member.isRepresentative()) {
			throw new BusinessException(MemberErrorCode.MEMBER_CONFLICT, MemberReason.REPRESENTATIVE)
				.addContext("memberId", memberId);
		}
		LocalDateTime now = LocalDateTime.now();
		member.leave(now);
		// 내보낸 운영진이 공용 링크로 돌아오지 못하게 유효한 링크를 모두 만료
		inviteLinkRepository.findAllByOrganizationIdAndExpiresAtAfter(organizationId, now)
			.forEach(link -> link.expire(now));
		// 나간 운영진이 걸린 대기 중 대표자 인수 요청은 취소
		representativeTransferRepository.findAllByMemberAndStatus(member, TransferStatus.PENDING)
			.forEach(transfer -> transfer.cancel(now));
		// 방금 나간 소속은 빼고 셈
		boolean stillOperator = memberRepository.existsByUserIdAndLeftAtIsNull(member.getUser().getId());
		member.getUser().leaveOrganization(stillOperator);
	}

	private static BusinessException memberNotFound(Long memberId) {
		return new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND).addContext("memberId", memberId);
	}

	private static BusinessException forbidden(Long organizationId, Long userId) {
		return new BusinessException(CommonErrorCode.FORBIDDEN)
			.addContext("organizationId", organizationId)
			.addContext("userId", userId);
	}

}
