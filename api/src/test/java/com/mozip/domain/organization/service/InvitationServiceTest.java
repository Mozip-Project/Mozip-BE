package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.devtools.seed.SeedFixtures;
import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.entity.Invitation;
import com.mozip.domain.organization.entity.InvitationStatus;
import com.mozip.domain.organization.entity.InviteLink;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.Team;
import com.mozip.domain.organization.exception.MemberErrorCode;
import com.mozip.domain.organization.exception.MemberInputReason;
import com.mozip.domain.organization.exception.MemberReason;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.TeamRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;
import com.mozip.global.exception.ErrorCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvitationServiceTest {

	private static final String INVITEE_PHONE = "01099990032";
	private static final String NEW_USER_PHONE = "01099990034";

	@Autowired
	private InvitationService invitationService;

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private MemberService memberService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private TeamRepository teamRepository;

	private User representative;
	private User invitee;
	private User outsider;
	private Organization organization;
	private Team team;

	@BeforeEach
	void setUp() {
		representative = saveUser("01099990031", "대표자");
		invitee = saveUser(INVITEE_PHONE, "초대받은 회원");
		outsider = saveUser("01099990033", "외부인");
		organization = organizationService.createOrganization(representative.getId(),
			SeedFixtures.createOrganizationInput());
		team = teamRepository.save(Team.create(organization, "기획팀", 0));
	}

	@Test
	@DisplayName("가입자를 휴대폰 번호(하이픈 포함)로 초대하면 invitee가 채워지고, 수락하면 그 팀의 OPERATOR가 된다")
	void inviteRegisteredUserThenAccept() {
		Invitation invitation = invitationService.inviteMember(representative.getId(), organization.getId(),
			"010-9999-0032", team.getId());

		assertThat(invitation.getInviteeContact()).isEqualTo(INVITEE_PHONE);
		assertThat(invitation.getInvitee().getId()).isEqualTo(invitee.getId());
		assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.PENDING);
		assertThat(invitation.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(6));
		assertThat(invitation.getCode()).hasSize(43);

		Member member = invitationService.acceptInvitation(invitee.getId(), invitation.getCode());

		assertThat(member.getRole()).isEqualTo(MemberRole.OPERATOR);
		assertThat(member.getTeam().getId()).isEqualTo(team.getId());
		assertThat(member.getOrganization().getId()).isEqualTo(organization.getId());
		assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.ACCEPTED);
		assertThat(invitation.getRespondedAt()).isNotNull();
	}

	@Test
	@DisplayName("미가입자는 연락처만 저장하고, 나중에 그 번호로 가입한 회원이 수락하면 invitee가 채워진다")
	void inviteUnregisteredThenAcceptAfterSignUp() {
		Invitation invitation = invitationService.inviteMember(representative.getId(), organization.getId(),
			NEW_USER_PHONE, null);
		assertThat(invitation.getInvitee()).isNull();

		User signedUp = saveUser(NEW_USER_PHONE, "새 회원");
		Member member = invitationService.acceptInvitation(signedUp.getId(), invitation.getCode());

		assertThat(member.getTeam()).isNull();
		assertThat(invitation.getInvitee().getId()).isEqualTo(signedUp.getId());
	}

	@Test
	@DisplayName("이메일 초대는 소문자로 저장하고 invitee는 비워 둔다")
	void inviteByEmail() {
		Invitation invitation = invitationService.inviteMember(representative.getId(), organization.getId(),
			" Someone@Example.COM ", null);

		assertThat(invitation.getInviteeContact()).isEqualTo("someone@example.com");
		assertThat(invitation.getInvitee()).isNull();
	}

	@Test
	@DisplayName("이미 운영진이면 ALREADY_MEMBER, 대기 중 초대가 있으면 ALREADY_INVITED, 형식이 틀리면 CONTACT_INVALID")
	void inviteRejected() {
		assertConflict(() -> invitationService.inviteMember(representative.getId(), organization.getId(),
			"01099990031", null), MemberReason.ALREADY_MEMBER);

		invitationService.inviteMember(representative.getId(), organization.getId(), INVITEE_PHONE, null);
		assertConflict(() -> invitationService.inviteMember(representative.getId(), organization.getId(),
			INVITEE_PHONE, null), MemberReason.ALREADY_INVITED);

		assertError(() -> invitationService.inviteMember(representative.getId(), organization.getId(), "12345",
			null), CommonErrorCode.INVALID_INPUT, MemberInputReason.CONTACT_INVALID.name());
		assertError(() -> invitationService.inviteMember(outsider.getId(), organization.getId(), NEW_USER_PHONE,
			null), CommonErrorCode.FORBIDDEN, null);
	}

	@Test
	@DisplayName("만료된 대기 초대는 중복으로 보지 않아 다시 초대할 수 있다")
	void reinviteAfterExpiry() {
		Invitation expired = invitationService.inviteMember(representative.getId(), organization.getId(),
			INVITEE_PHONE, null);
		ReflectionTestUtils.setField(expired, "expiresAt", LocalDateTime.now().minusMinutes(1));

		Invitation again = invitationService.inviteMember(representative.getId(), organization.getId(),
			INVITEE_PHONE, null);

		assertThat(again.getId()).isNotEqualTo(expired.getId());
	}

	@Test
	@DisplayName("다른 단체 팀이나 팀 없는 단체의 팀 지정은 COMMON_400이다")
	void inviteWithInvalidTeam() {
		Organization other = organizationService.createOrganization(representative.getId(),
			SeedFixtures.createOrganizationInput());
		Team otherTeam = teamRepository.save(Team.create(other, "다른 팀", 0));
		assertError(() -> invitationService.inviteMember(representative.getId(), organization.getId(),
			INVITEE_PHONE, otherTeam.getId()), CommonErrorCode.INVALID_INPUT, MemberInputReason.TEAM_NOT_FOUND.name());

		Organization noTeam = organizationService.createOrganization(representative.getId(),
			new CreateOrganizationInput("팀 없는 단체", OrganizationType.CLUB, null, OrganizationScope.UNION, null, true,
				null, null));
		assertError(() -> invitationService.inviteMember(representative.getId(), noTeam.getId(), INVITEE_PHONE,
			team.getId()), CommonErrorCode.INVALID_INPUT, MemberInputReason.NO_TEAM_ORGANIZATION.name());
	}

	@Test
	@DisplayName("수락은 없는 코드 MEMBER_404, 만료 MEMBER_410, 처리된 초대 ALREADY_HANDLED, 남의 초대 COMMON_403이다")
	void acceptRejected() {
		assertError(() -> invitationService.acceptInvitation(invitee.getId(), "unknown-code"),
			MemberErrorCode.MEMBER_NOT_FOUND, null);

		Invitation invitation = invitationService.inviteMember(representative.getId(), organization.getId(),
			INVITEE_PHONE, null);
		assertError(() -> invitationService.acceptInvitation(outsider.getId(), invitation.getCode()),
			CommonErrorCode.FORBIDDEN, null);

		ReflectionTestUtils.setField(invitation, "expiresAt", LocalDateTime.now().minusMinutes(1));
		assertError(() -> invitationService.acceptInvitation(invitee.getId(), invitation.getCode()),
			MemberErrorCode.INVITATION_EXPIRED, null);

		ReflectionTestUtils.setField(invitation, "expiresAt", LocalDateTime.now().plusDays(1));
		invitationService.acceptInvitation(invitee.getId(), invitation.getCode());
		assertConflict(() -> invitationService.acceptInvitation(invitee.getId(), invitation.getCode()),
			MemberReason.ALREADY_HANDLED);
	}

	@Test
	@DisplayName("초대 뒤 다른 경로로 이미 운영진이 됐으면 수락은 ALREADY_MEMBER다")
	void acceptWhenAlreadyMember() {
		Invitation invitation = invitationService.inviteMember(representative.getId(), organization.getId(),
			INVITEE_PHONE, null);
		memberRepository.save(Member.create(organization, invitee, null, MemberRole.OPERATOR));

		assertConflict(() -> invitationService.acceptInvitation(invitee.getId(), invitation.getCode()),
			MemberReason.ALREADY_MEMBER);
	}

	@Test
	@DisplayName("개인 초대를 받은 회원이 공용 링크로 먼저 합류하면 그 초대는 CANCELED가 되어 내보낸 뒤 다시 쓸 수 없다")
	void joinByLinkCancelsPendingInvitation() {
		Invitation invitation = invitationService.inviteMember(representative.getId(), organization.getId(),
			INVITEE_PHONE, null);
		InviteLink link = invitationService.createInviteLink(representative.getId(), organization.getId());

		Member member = invitationService.acceptInvitation(invitee.getId(), link.getToken());

		assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.CANCELED);
		assertThat(invitation.getRespondedAt()).isNotNull();

		memberService.removeMember(representative.getId(), member.getId());
		memberRepository.flush();

		assertConflict(() -> invitationService.acceptInvitation(invitee.getId(), invitation.getCode()),
			MemberReason.ALREADY_HANDLED);
	}

	@Test
	@DisplayName("공용 링크는 유효한 동안 같은 링크를 돌려주고, 누구나 토큰으로 팀 없이 합류한다")
	void inviteLink() {
		InviteLink link = invitationService.createInviteLink(representative.getId(), organization.getId());
		InviteLink same = invitationService.createInviteLink(representative.getId(), organization.getId());
		assertThat(same.getId()).isEqualTo(link.getId());
		assertThat(link.getToken()).hasSize(43);

		Member member = invitationService.acceptInvitation(outsider.getId(), link.getToken());
		assertThat(member.getRole()).isEqualTo(MemberRole.OPERATOR);
		assertThat(member.getTeam()).isNull();

		assertConflict(() -> invitationService.acceptInvitation(outsider.getId(), link.getToken()),
			MemberReason.ALREADY_MEMBER);
	}

	@Test
	@DisplayName("만료된 공용 링크는 수락할 수 없고(MEMBER_410), 새로 발급하면 다른 링크가 나온다")
	void expiredInviteLink() {
		InviteLink link = invitationService.createInviteLink(representative.getId(), organization.getId());
		ReflectionTestUtils.setField(link, "expiresAt", LocalDateTime.now().minusMinutes(1));

		assertError(() -> invitationService.acceptInvitation(outsider.getId(), link.getToken()),
			MemberErrorCode.INVITATION_EXPIRED, null);
		assertThat(invitationService.createInviteLink(representative.getId(), organization.getId()).getId())
			.isNotEqualTo(link.getId());
	}

	@Test
	@DisplayName("만료가 하루 안으로 남은 공용 링크는 재사용하지 않고 새로 발급한다")
	void inviteLinkNearExpiry() {
		InviteLink link = invitationService.createInviteLink(representative.getId(), organization.getId());
		ReflectionTestUtils.setField(link, "expiresAt", LocalDateTime.now().plusHours(12));
		memberRepository.flush();

		assertThat(invitationService.createInviteLink(representative.getId(), organization.getId()).getId())
			.isNotEqualTo(link.getId());
	}

	@Test
	@DisplayName("운영진을 내보내면 유효한 공용 링크가 만료되어 그 링크로 다시 들어올 수 없다")
	void removeMemberExpiresInviteLink() {
		InviteLink link = invitationService.createInviteLink(representative.getId(), organization.getId());
		Member member = invitationService.acceptInvitation(invitee.getId(), link.getToken());

		memberService.removeMember(representative.getId(), member.getId());
		memberRepository.flush();

		assertError(() -> invitationService.acceptInvitation(invitee.getId(), link.getToken()),
			MemberErrorCode.INVITATION_EXPIRED, null);
	}

	@Test
	@DisplayName("내보낸 운영진도 다시 초대하고 수락할 수 있다")
	void reinviteRemovedMember() {
		Member removed = memberRepository.save(Member.create(organization, invitee, null, MemberRole.OPERATOR));
		memberService.removeMember(representative.getId(), removed.getId());
		// 내보내기를 먼저 DB에 반영
		memberRepository.flush();

		Invitation invitation = invitationService.inviteMember(representative.getId(), organization.getId(),
			INVITEE_PHONE, null);
		Member rejoined = invitationService.acceptInvitation(invitee.getId(), invitation.getCode());
		memberRepository.flush();

		assertThat(rejoined.getId()).isNotEqualTo(removed.getId());
	}

	private User saveUser(String phone, String name) {
		return userRepository.save(User.create(phone, "encoded", name, false, LocalDateTime.now()));
	}

	private static void assertConflict(Runnable action, MemberReason reason) {
		assertError(action, MemberErrorCode.MEMBER_CONFLICT, reason.name());
	}

	private static void assertError(Runnable action, ErrorCode errorCode, String reason) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> {
			assertThat(e.getErrorCode()).isEqualTo(errorCode);
			assertThat(e.getReason()).isEqualTo(reason);
		});
	}

}
