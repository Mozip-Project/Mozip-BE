package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.devtools.seed.SeedFixtures;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.RepresentativeTransfer;
import com.mozip.domain.organization.entity.TransferStatus;
import com.mozip.domain.organization.exception.MemberErrorCode;
import com.mozip.domain.organization.exception.MemberInputReason;
import com.mozip.domain.organization.exception.MemberReason;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;
import com.mozip.global.exception.ErrorCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class RepresentativeTransferServiceTest {

	@Autowired
	private RepresentativeTransferService transferService;

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private MemberService memberService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MemberRepository memberRepository;

	private User representative;
	private User operator;
	private Organization organization;
	private Member representativeMember;
	private Member operatorMember;

	@BeforeEach
	void setUp() {
		representative = saveUser();
		operator = saveUser();
		organization = organizationService.createOrganization(representative.getId(),
			SeedFixtures.createOrganizationInput());
		representativeMember = memberRepository.findByOrganizationIdAndUserIdAndRoleAndLeftAtIsNull(
			organization.getId(), representative.getId(), MemberRole.REPRESENTATIVE).orElseThrow();
		operatorMember = memberRepository.save(Member.create(organization, operator, null, MemberRole.OPERATOR));
	}

	@Test
	@DisplayName("대상이 수락하면 두 역할이 바뀌고 대표자 unique index도 통과한다")
	void approve() {
		RepresentativeTransfer transfer = transferService.request(representative.getId(), operatorMember.getId());
		assertThat(transfer.getStatus()).isEqualTo(TransferStatus.PENDING);

		RepresentativeTransfer approved = transferService.respond(operator.getId(), transfer.getId(), true);
		// 역할 교체를 DB에 반영
		memberRepository.flush();

		assertThat(approved.getStatus()).isEqualTo(TransferStatus.APPROVED);
		assertThat(approved.getRespondedAt()).isNotNull();
		assertThat(memberRepository.findById(representativeMember.getId()).orElseThrow().getRole())
			.isEqualTo(MemberRole.OPERATOR);
		assertThat(memberRepository.findById(operatorMember.getId()).orElseThrow().getRole())
			.isEqualTo(MemberRole.REPRESENTATIVE);
	}

	@Test
	@DisplayName("거절하면 역할은 그대로고 상태만 REJECTED가 된다")
	void reject() {
		RepresentativeTransfer transfer = transferService.request(representative.getId(), operatorMember.getId());

		transferService.respond(operator.getId(), transfer.getId(), false);

		assertThat(transfer.getStatus()).isEqualTo(TransferStatus.REJECTED);
		assertThat(representativeMember.getRole()).isEqualTo(MemberRole.REPRESENTATIVE);
		assertThat(operatorMember.getRole()).isEqualTo(MemberRole.OPERATOR);
	}

	@Test
	@DisplayName("대표자가 아니면 COMMON_403, 본인 지정은 COMMON_400, 없는 운영진은 MEMBER_404다")
	void requestRejected() {
		assertError(() -> transferService.request(operator.getId(), representativeMember.getId()),
			CommonErrorCode.FORBIDDEN, null);
		assertError(() -> transferService.request(representative.getId(), representativeMember.getId()),
			CommonErrorCode.INVALID_INPUT, MemberInputReason.TRANSFER_TO_SELF.name());
		assertError(() -> transferService.request(representative.getId(), Long.MAX_VALUE),
			MemberErrorCode.MEMBER_NOT_FOUND, null);
	}

	@Test
	@DisplayName("대기 중 요청이 있으면 다른 운영진에게도 요청할 수 없다 (TRANSFER_IN_PROGRESS)")
	void duplicateRequest() {
		Member another = memberRepository.save(Member.create(organization, saveUser(), null, MemberRole.OPERATOR));
		transferService.request(representative.getId(), operatorMember.getId());

		assertError(() -> transferService.request(representative.getId(), another.getId()),
			MemberErrorCode.MEMBER_CONFLICT, MemberReason.TRANSFER_IN_PROGRESS.name());
	}

	@Test
	@DisplayName("요청 대상이 아니면 응답할 수 없고(COMMON_403), 처리된 요청은 다시 응답할 수 없다(ALREADY_HANDLED)")
	void respondRejected() {
		RepresentativeTransfer transfer = transferService.request(representative.getId(), operatorMember.getId());

		assertError(() -> transferService.respond(representative.getId(), transfer.getId(), true),
			CommonErrorCode.FORBIDDEN, null);

		transferService.respond(operator.getId(), transfer.getId(), false);
		assertError(() -> transferService.respond(operator.getId(), transfer.getId(), true),
			MemberErrorCode.MEMBER_CONFLICT, MemberReason.ALREADY_HANDLED.name());
		assertError(() -> transferService.respond(operator.getId(), Long.MAX_VALUE, true),
			MemberErrorCode.MEMBER_NOT_FOUND, null);
	}

	@Test
	@DisplayName("대상을 내보내면 대기 요청이 CANCELED가 되고 새 요청을 할 수 있다")
	void cancelWhenTargetRemoved() {
		RepresentativeTransfer transfer = transferService.request(representative.getId(), operatorMember.getId());

		memberService.removeMember(representative.getId(), operatorMember.getId());

		assertThat(transfer.getStatus()).isEqualTo(TransferStatus.CANCELED);
		// 취소 상태를 DB에 반영
		memberRepository.flush();
		Member another = memberRepository.save(Member.create(organization, saveUser(), null, MemberRole.OPERATOR));
		assertThat(transferService.request(representative.getId(), another.getId()).getStatus())
			.isEqualTo(TransferStatus.PENDING);
	}

	private User saveUser() {
		String phone = "0109" + String.format("%07d", ThreadLocalRandom.current().nextInt(10_000_000));
		return userRepository.save(User.create(phone, "encoded", "테스트", false, LocalDateTime.now()));
	}

	private static void assertError(Runnable action, ErrorCode errorCode, String reason) {
		assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class, e -> {
			assertThat(e.getErrorCode()).isEqualTo(errorCode);
			assertThat(e.getReason()).isEqualTo(reason);
		});
	}

}
