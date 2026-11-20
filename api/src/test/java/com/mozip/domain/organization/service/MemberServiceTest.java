package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;

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
import com.mozip.domain.organization.exception.MemberErrorCode;
import com.mozip.domain.organization.exception.MemberReason;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

// 테스트 메서드마다 트랜잭션을 열고 끝나면 롤백해 다른 테스트에 데이터가 남지 않음
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MemberServiceTest {

	@Autowired
	private MemberService memberService;

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private OrganizationAccessChecker accessChecker;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private MemberRepository memberRepository;

	private User representative;
	private User operator;
	private User outsider;
	private Organization organization;
	private Member representativeMember;
	private Member operatorMember;

	@BeforeEach
	void setUp() {
		representative = userRepository.save(User.create("01099990021", "encoded", "대표자", false, LocalDateTime.now()));
		operator = userRepository.save(User.create("01099990022", "encoded", "운영진", false, LocalDateTime.now()));
		outsider = userRepository.save(User.create("01099990023", "encoded", "외부인", false, LocalDateTime.now()));
		organization = organizationService.createOrganization(representative.getId(),
			SeedFixtures.createOrganizationInput());
		representativeMember = memberRepository.findAllByUserIdAndLeftAtIsNull(representative.getId()).getFirst();
		operatorMember = memberRepository.save(Member.create(organization, operator, null, MemberRole.OPERATOR));
	}

	@Test
	@DisplayName("운영진 목록은 대표자 먼저, 그 뒤 합류일 순이고 내보낸 운영진은 빠진다")
	void getMembers() {
		User later = userRepository.save(User.create("01099990024", "encoded", "나중 합류", false, LocalDateTime.now()));
		Member laterMember = memberRepository.save(Member.create(organization, later, null, MemberRole.OPERATOR));

		List<Member> members = memberService.getMembers(operator.getId(), organization.getId());

		assertThat(members).extracting(Member::getId)
			.containsExactly(representativeMember.getId(), operatorMember.getId(), laterMember.getId());

		memberService.removeMember(representative.getId(), operatorMember.getId());

		assertThat(memberService.getMembers(representative.getId(), organization.getId()))
			.extracting(Member::getId)
			.containsExactly(representativeMember.getId(), laterMember.getId());
	}

	@Test
	@DisplayName("단체 운영진이 아니면 목록을 볼 수 없고(COMMON_403), 없는 단체면 ORG_404다")
	void getMembersForbiddenOrNotFound() {
		assertThatThrownBy(() -> memberService.getMembers(outsider.getId(), organization.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
		assertThatThrownBy(() -> memberService.getMembers(representative.getId(), Long.MAX_VALUE))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
	}

	@Test
	@DisplayName("대표자가 운영진을 내보내면 leftAt이 찍히고 소속 검사에서 빠지며 lastOrganization이 비워진다")
	void removeMember() {
		organizationService.selectOrganization(operator.getId(), organization.getId());

		memberService.removeMember(representative.getId(), operatorMember.getId());

		assertThat(operatorMember.getLeftAt()).isNotNull();
		assertThat(accessChecker.isOperatorOf(operator.getId(), organization.getId())).isFalse();
		assertThat(memberRepository.findAllWithOrganizationAndTeamByUserId(operator.getId())).isEmpty();
		assertThat(operator.getLastOrganization()).isNull();
	}

	@Test
	@DisplayName("내보낸 회원의 lastOrganization이 다른 단체면 그대로 둔다")
	void removeMemberKeepsOtherLastOrganization() {
		Organization other = organizationService.createOrganization(operator.getId(),
			SeedFixtures.createOrganizationInput());
		organizationService.selectOrganization(operator.getId(), other.getId());

		memberService.removeMember(representative.getId(), operatorMember.getId());

		assertThat(operator.getLastOrganization().getId()).isEqualTo(other.getId());
	}

	@Test
	@DisplayName("내보낸 회원은 같은 단체에 다시 등록할 수 있다 (부분 unique index)")
	void rejoinAfterRemoval() {
		memberService.removeMember(representative.getId(), operatorMember.getId());
		// 실제로는 내보내기 트랜잭션이 먼저 커밋됨. 한 트랜잭션 안에서는 Hibernate가 insert를 update보다 먼저 보내므로 flush로 순서를 맞춤
		memberRepository.flush();

		Member rejoined = memberRepository.saveAndFlush(
			Member.create(organization, operator, null, MemberRole.OPERATOR));

		assertThat(rejoined.getId()).isNotEqualTo(operatorMember.getId());
		assertThat(accessChecker.isOperatorOf(operator.getId(), organization.getId())).isTrue();
	}

	@Test
	@DisplayName("대표자가 아니면 내보낼 수 없다 (COMMON_403)")
	void removeMemberForbidden() {
		assertThatThrownBy(() -> memberService.removeMember(operator.getId(), representativeMember.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
		assertThatThrownBy(() -> memberService.removeMember(outsider.getId(), operatorMember.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
		assertThat(operatorMember.getLeftAt()).isNull();
	}

	@Test
	@DisplayName("대표자 본인은 내보낼 수 없다 (MEMBER_409, reason REPRESENTATIVE)")
	void removeRepresentative() {
		assertThatThrownBy(() -> memberService.removeMember(representative.getId(), representativeMember.getId()))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_CONFLICT);
				assertThat(e.getReason()).isEqualTo(MemberReason.REPRESENTATIVE.name());
			});
	}

	@Test
	@DisplayName("없는 운영진이나 이미 나간 운영진이면 MEMBER_404다")
	void removeMemberNotFound() {
		assertThatThrownBy(() -> memberService.removeMember(representative.getId(), Long.MAX_VALUE))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));

		memberService.removeMember(representative.getId(), operatorMember.getId());

		assertThatThrownBy(() -> memberService.removeMember(representative.getId(), operatorMember.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND));
	}

}
