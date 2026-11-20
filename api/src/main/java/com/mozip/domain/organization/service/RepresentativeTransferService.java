package com.mozip.domain.organization.service;

import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.notification.entity.NotificationType;
import com.mozip.domain.notification.service.NotificationService;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.RepresentativeTransfer;
import com.mozip.domain.organization.entity.TransferStatus;
import com.mozip.domain.organization.exception.MemberErrorCode;
import com.mozip.domain.organization.exception.MemberInputReason;
import com.mozip.domain.organization.exception.MemberReason;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.RepresentativeTransferRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 대표자 인수 (대표자가 운영진에게 요청, 대상이 수락하면 역할 교체)
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RepresentativeTransferService {

	private final RepresentativeTransferRepository transferRepository;
	private final MemberRepository memberRepository;
	private final NotificationService notificationService;

	// 대상 운영진으로 단체를 정한 뒤 호출자가 그 단체 대표자인지 확인. 대기 요청 중복은 부분 unique index가 최종 방어선
	@Transactional
	public RepresentativeTransfer request(Long userId, Long memberId) {
		Member target = memberRepository.findActiveWithOrganizationAndUserById(memberId)
			.orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND).addContext("memberId", memberId));
		Long organizationId = target.getOrganization().getId();
		Member representative = memberRepository.findByOrganizationIdAndUserIdAndRoleAndLeftAtIsNull(organizationId,
				userId, MemberRole.REPRESENTATIVE)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("organizationId", organizationId)
				.addContext("userId", userId));
		if (representative.getId().equals(target.getId())) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, MemberInputReason.TRANSFER_TO_SELF);
		}
		if (transferRepository.existsByOrganizationIdAndStatus(organizationId, TransferStatus.PENDING)) {
			throw new BusinessException(MemberErrorCode.MEMBER_CONFLICT, MemberReason.TRANSFER_IN_PROGRESS)
				.addContext("organizationId", organizationId);
		}

		Long transferId = transferRepository.saveAndFlush(RepresentativeTransfer.create(representative, target)).getId();
		notificationService.send(target.getUser().getId(), NotificationType.TRANSFER_REQUEST, Map.of(
			"organizationId", organizationId, "transferId", transferId));
		// 대표자 행의 user/team이 지연 로딩 프록시로 남아 있어 응답에 필요한 연관을 fetch join으로 다시 채움
		return transferRepository.findWithMembersById(transferId).orElseThrow();
	}

	// 요청 대상만 응답 가능. 수락하면 한 트랜잭션에서 기존 대표자 → OPERATOR, 대상 → REPRESENTATIVE
	@Transactional
	public RepresentativeTransfer respond(Long userId, Long transferId, boolean accept) {
		RepresentativeTransfer transfer = transferRepository.findWithMembersById(transferId)
			.orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)
				.addContext("transferId", transferId));
		Member from = transfer.getFromMember();
		Member to = transfer.getToMember();
		if (!to.getUser().getId().equals(userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("transferId", transferId)
				.addContext("userId", userId);
		}
		if (!transfer.isPending()) {
			throw new BusinessException(MemberErrorCode.MEMBER_CONFLICT, MemberReason.ALREADY_HANDLED)
				.addContext("transferId", transferId);
		}

		LocalDateTime now = LocalDateTime.now();
		if (!accept) {
			transfer.reject(now);
			notifyRequester(transfer, NotificationType.TRANSFER_REJECTED);
			return transfer;
		}
		// 대표자 unique index(단체당 소속 중인 REPRESENTATIVE 1명)는 문장마다 검사돼, 기존 대표자를 먼저 내려 DB에 반영한 뒤 대상을 올림
		from.changeRole(MemberRole.OPERATOR);
		memberRepository.flush();
		to.changeRole(MemberRole.REPRESENTATIVE);
		transfer.approve(now);
		notifyRequester(transfer, NotificationType.TRANSFER_APPROVED);
		return transfer;
	}

	private void notifyRequester(RepresentativeTransfer transfer, NotificationType type) {
		notificationService.send(transfer.getFromMember().getUser().getId(), type, Map.of(
			"organizationId", transfer.getOrganization().getId(), "transferId", transfer.getId()));
	}

}
