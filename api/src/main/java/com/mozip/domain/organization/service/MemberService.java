package com.mozip.domain.organization.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.exception.MemberErrorCode;
import com.mozip.domain.organization.exception.MemberReason;
import com.mozip.domain.organization.repository.InviteLinkRepository;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
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
	private final OrganizationRepository organizationRepository;
	private final OrganizationService organizationService;

	// 그 단체 운영진만 조회 가능, 없으면 ORG_404이고 소속이 아니면 COMMON_403
	public List<Member> getMembers(Long userId, Long organizationId) {
		organizationService.getOrganization(organizationId);
		if (!memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId)) {
			throw forbidden(organizationId, userId);
		}
		return memberRepository.findAllActiveWithUserAndTeamByOrganizationId(organizationId);
	}

	// 대표자만 내보낼 수 있고 대표자 본인은 내보낼 수 없음
	// 그 단체 소속이 아닌 사람에게는 운영진이 있어도 MEMBER_404로 응답해 memberId가 있는지 알 수 없게 함
	// 단체 행을 잠가 같은 단체의 수정, 내보내기가 차례로 처리되게 하고 운영진과 대표자 여부는 잠근 뒤에 읽음
	// 행은 남기고 leftAt을 찍으며, 남은 소속이 없으면 그 회원의 lastRole을 APPLICANT로
	@Transactional
	public void removeMember(Long userId, Long memberId) {
		Long organizationId = memberRepository.findOrganizationIdByActiveId(memberId)
			.orElseThrow(() -> notFound(memberId));
		if (!memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId)) {
			throw notFound(memberId);
		}
		organizationRepository.lockById(organizationId);
		Member member = memberRepository.findActiveWithOrganizationAndUserById(memberId)
			.orElseThrow(() -> notFound(memberId));
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
		// 방금 나간 소속은 빼고 셈
		boolean stillOperator = memberRepository.existsByUserIdAndLeftAtIsNull(member.getUser().getId());
		member.getUser().leaveOrganization(stillOperator);
	}

	private static BusinessException notFound(Long memberId) {
		return new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND).addContext("memberId", memberId);
	}

	private static BusinessException forbidden(Long organizationId, Long userId) {
		return new BusinessException(CommonErrorCode.FORBIDDEN)
			.addContext("organizationId", organizationId)
			.addContext("userId", userId);
	}

}
