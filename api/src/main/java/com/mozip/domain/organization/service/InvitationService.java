package com.mozip.domain.organization.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.notification.entity.NotificationType;
import com.mozip.domain.notification.service.NotificationService;
import com.mozip.domain.organization.entity.Invitation;
import com.mozip.domain.organization.entity.InvitationStatus;
import com.mozip.domain.organization.entity.InviteLink;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.exception.MemberErrorCode;
import com.mozip.domain.organization.exception.MemberInputReason;
import com.mozip.domain.organization.exception.MemberReason;
import com.mozip.domain.organization.repository.InvitationRepository;
import com.mozip.domain.organization.repository.InviteLinkRepository;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 운영진 초대 (개인 초대, 공용 링크)와 수락
// 미가입자 초대는 수락할 때 로그인 회원의 연락처와 비교
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InvitationService {

	private static final Pattern PHONE = Pattern.compile("01\\d{8,9}");
	private static final Pattern EMAIL = Pattern.compile("[^@\\s]+@[^@\\s]+\\.[^@\\s]+");
	private static final SecureRandom RANDOM = new SecureRandom();

	private final InvitationRepository invitationRepository;
	private final InviteLinkRepository inviteLinkRepository;
	private final MemberRepository memberRepository;
	private final TeamRepository teamRepository;
	private final UserRepository userRepository;
	private final OrganizationService organizationService;
	private final NotificationService notificationService;

	// 운영진만 초대 가능. 가입자(휴대폰 일치)면 invitee를 채우고 알림, 미가입자면 연락처만 저장
	@Transactional
	public Invitation inviteMember(Long userId, Long organizationId, String contact, Long teamId) {
		Organization organization = organizationService.getOrganizationForUpdate(organizationId);
		checkOperator(organizationId, userId);
		String normalizedContact = normalizeContact(contact);
		Team team = findTeam(organization, teamId);

		// TODO(A 회원 연락처 이메일): contactEmail 컬럼이 생기면 이메일로도 가입자를 찾음, 지금은 휴대폰만
		User invitee = isEmail(normalizedContact) ? null
			: userRepository.findWithSchoolByPhone(normalizedContact).orElse(null);
		if (invitee != null && memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId,
			invitee.getId())) {
			throw conflict(MemberReason.ALREADY_MEMBER).addContext("inviteeId", invitee.getId());
		}
		LocalDateTime now = LocalDateTime.now();
		boolean alreadyInvited = invitationRepository.findAllByOrganizationIdAndInviteeContactAndStatus(
			organizationId, normalizedContact, InvitationStatus.PENDING).stream()
			.anyMatch(invitation -> !invitation.isExpired(now));
		if (alreadyInvited) {
			throw conflict(MemberReason.ALREADY_INVITED).addContext("organizationId", organizationId);
		}

		User inviter = userRepository.findWithSchoolById(userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND).addContext("userId", userId));
		Invitation invitation = invitationRepository.save(Invitation.create(organization, inviter, invitee,
			normalizedContact, team, newToken(), now));
		if (invitee != null) {
			notificationService.send(invitee.getId(), NotificationType.INVITATION, Map.of(
				"organizationId", organizationId, "invitationId", invitation.getId(), "code", invitation.getCode()));
		}
		return invitation;
	}

	// 운영진만 발급 가능, 유효한 링크가 있으면 그대로 돌려주고 만료가 하루 안으로 남았으면 새로 발급
	@Transactional
	public InviteLink createInviteLink(Long userId, Long organizationId) {
		Organization organization = organizationService.getOrganizationForUpdate(organizationId);
		checkOperator(organizationId, userId);
		LocalDateTime now = LocalDateTime.now();
		return inviteLinkRepository.findFirstByOrganizationIdAndExpiresAtAfterOrderByExpiresAtDesc(organizationId,
				now.plusDays(1))
			.orElseGet(() -> inviteLinkRepository.save(InviteLink.create(organization, newToken(), now)));
	}

	// 개인 초대 코드를 먼저 찾고 없으면 공용 링크 토큰으로 찾음. 둘 다 없으면 MEMBER_404
	@Transactional
	public Member acceptInvitation(Long userId, String code) {
		User user = userRepository.findWithSchoolById(userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND).addContext("userId", userId));
		LocalDateTime now = LocalDateTime.now();
		Optional<Invitation> invitation = invitationRepository.findWithOrganizationAndTeamByCode(code);
		if (invitation.isPresent()) {
			return acceptPersonal(invitation.get(), user, now);
		}
		InviteLink link = inviteLinkRepository.findWithOrganizationByToken(code)
			.orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND).addContext("code", code));
		if (link.isExpired(now)) {
			throw new BusinessException(MemberErrorCode.INVITATION_EXPIRED).addContext("inviteLinkId", link.getId());
		}
		return join(link.getOrganization(), user, null);
	}

	// 처리 여부 → 만료 → 본인 확인 → 이미 운영진 순으로 검사. 처리된 초대는 만료 시각과 상관없이 ALREADY_HANDLED
	private Member acceptPersonal(Invitation invitation, User user, LocalDateTime now) {
		if (!invitation.isPending()) {
			throw conflict(MemberReason.ALREADY_HANDLED).addContext("invitationId", invitation.getId());
		}
		if (invitation.isExpired(now)) {
			throw new BusinessException(MemberErrorCode.INVITATION_EXPIRED)
				.addContext("invitationId", invitation.getId());
		}
		if (!isInvitee(invitation, user)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("invitationId", invitation.getId())
				.addContext("userId", user.getId());
		}
		Member member = join(invitation.getOrganization(), user, invitation.getTeam());
		invitation.accept(user, now);
		return member;
	}

	// 초대 시점에 가입자였으면 그 회원, 아니면 연락처가 같은 회원만 본인
	private static boolean isInvitee(Invitation invitation, User user) {
		if (invitation.getInvitee() != null) {
			return invitation.getInvitee().getId().equals(user.getId());
		}
		// TODO(A 회원 연락처 이메일): contactEmail이 생기면 이메일도 비교, 지금은 휴대폰만
		return invitation.getInviteeContact().equals(user.getPhone());
	}

	private Member join(Organization organization, User user, Team team) {
		if (memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organization.getId(), user.getId())) {
			throw conflict(MemberReason.ALREADY_MEMBER).addContext("organizationId", organization.getId());
		}
		// 동시 수락으로 소속 unique에 걸리면 MEMBER_409로 응답
		try {
			return memberRepository.saveAndFlush(Member.create(organization, user, team, MemberRole.OPERATOR));
		} catch (DataIntegrityViolationException e) {
			throw conflict(MemberReason.ALREADY_MEMBER).addContext("organizationId", organization.getId());
		}
	}

	private Team findTeam(Organization organization, Long teamId) {
		if (teamId == null) {
			return null;
		}
		if (organization.isNoTeam()) {
			throw invalid(MemberInputReason.NO_TEAM_ORGANIZATION);
		}
		return teamRepository.findByIdAndOrganizationId(teamId, organization.getId())
			.orElseThrow(() -> invalid(MemberInputReason.TEAM_NOT_FOUND).addContext("teamId", teamId));
	}

	private void checkOperator(Long organizationId, Long userId) {
		if (!memberRepository.existsByOrganizationIdAndUserIdAndLeftAtIsNull(organizationId, userId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("organizationId", organizationId)
				.addContext("userId", userId);
		}
	}

	// 이메일은 소문자로, 휴대폰은 숫자만 남김
	static String normalizeContact(String contact) {
		String trimmed = contact == null ? "" : contact.strip();
		if (trimmed.contains("@")) {
			String email = trimmed.toLowerCase();
			if (EMAIL.matcher(email).matches() && email.length() <= 255) {
				return email;
			}
			throw invalid(MemberInputReason.CONTACT_INVALID);
		}
		String phone = trimmed.replaceAll("[\\s-]", "");
		if (PHONE.matcher(phone).matches()) {
			return phone;
		}
		throw invalid(MemberInputReason.CONTACT_INVALID);
	}

	private static boolean isEmail(String normalizedContact) {
		return normalizedContact.contains("@");
	}

	// SecureRandom 32바이트로 만든 43자 URL-safe 토큰
	private static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	private static BusinessException conflict(MemberReason reason) {
		return new BusinessException(MemberErrorCode.MEMBER_CONFLICT, reason);
	}

	private static BusinessException invalid(MemberInputReason reason) {
		return new BusinessException(CommonErrorCode.INVALID_INPUT, reason);
	}

}
