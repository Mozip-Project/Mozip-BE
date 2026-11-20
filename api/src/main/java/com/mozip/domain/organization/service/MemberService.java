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
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.RepresentativeTransferRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 운영진 관리 (목록, 내보내기). 초대는 InvitationService, 대표자 인수는 RepresentativeTransferService
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

	private final MemberRepository memberRepository;
	private final OrganizationService organizationService;
	private final RepresentativeTransferRepository representativeTransferRepository;

	// 그 단체 운영진만 조회 가능. 없는 단체(ORG_404)를 먼저 거르고 소속이 아니면 COMMON_403
	public List<Member> getMembers(Long userId, Long organizationId) {
		organizationService.getOrganization(organizationId);
		if (!memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId)) {
			throw forbidden(organizationId, userId);
		}
		return memberRepository.findAllActiveWithUserAndTeamByOrganizationId(organizationId);
	}

	// 대표자만 내보낼 수 있고 대표자 행은 못 내보냄. 단체당 대표자가 1명이라 대표자 행은 곧 호출자 본인
	// 행은 남기고 leftAt만 찍어 평가 기록을 보존하고, 드롭다운이 나간 단체를 현재 단체로 잡지 않게 lastOrganization도 정리
	@Transactional
	public void removeMember(Long userId, Long memberId) {
		Member member = memberRepository.findActiveWithOrganizationAndUserById(memberId)
			.orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND).addContext("memberId", memberId));
		Long organizationId = member.getOrganization().getId();
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
		member.getUser().leaveOrganization(member.getOrganization());
		// 나간 운영진이 대상인 대표자 인수 요청은 수락할 사람이 없어지므로 취소
		representativeTransferRepository.findAllByMemberAndStatus(member, TransferStatus.PENDING)
			.forEach(transfer -> transfer.cancel(now));
	}

	private static BusinessException forbidden(Long organizationId, Long userId) {
		return new BusinessException(CommonErrorCode.FORBIDDEN)
			.addContext("organizationId", organizationId)
			.addContext("userId", userId);
	}

}
