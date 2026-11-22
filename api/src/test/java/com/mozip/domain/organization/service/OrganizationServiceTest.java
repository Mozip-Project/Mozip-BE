package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.devtools.seed.SeedFixtures;
import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.entity.CouncilLevel;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.exception.OrganizationInputReason;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.config.S3Properties;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrganizationServiceTest {

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private S3Properties s3Properties;

	private User user;
	private User other;
	private School school;

	@BeforeEach
	void setUp() {
		user = userRepository.save(User.create("01099990011", "encoded", "대표자", false, LocalDateTime.now()));
		other = userRepository.save(User.create("01099990012", "encoded", "다른 회원", false, LocalDateTime.now()));
		school = schoolRepository.save(School.create("테스트대학교", "test.ac.kr"));
	}

	@Test
	@DisplayName("단체를 만들면 PENDING 상태로 저장되고 호출자가 대표자로 등록된다")
	void createOrganization() {
		Organization organization = organizationService.createOrganization(user.getId(), new CreateOrganizationInput(
			"총학생회", OrganizationType.STUDENT_COUNCIL, CouncilLevel.UNIVERSITY, OrganizationScope.CAMPUS,
			school.getId(), true, "https://example.com/proof", null));

		assertThat(organization.getId()).isNotNull();
		assertThat(organization.getReviewStatus()).isEqualTo(ReviewStatus.PENDING);
		assertThat(organization.getCouncilLevel()).isEqualTo(CouncilLevel.UNIVERSITY);
		assertThat(organization.getSchool().getId()).isEqualTo(school.getId());
		assertThat(organization.isNoTeam()).isTrue();
		assertThat(organization.getProofLink()).isEqualTo("https://example.com/proof");

		List<Member> memberships = memberRepository.findAllByUserId(user.getId());
		assertThat(memberships).hasSize(1);
		assertThat(memberships.getFirst().getRole()).isEqualTo(MemberRole.REPRESENTATIVE);
		assertThat(memberships.getFirst().getOrganization().getId()).isEqualTo(organization.getId());
	}

	@Test
	@DisplayName("학생회인데 세부 구분이 없으면 COMMON_400 COUNCIL_LEVEL_REQUIRED다")
	void councilLevelRequired() {
		assertInvalid(new CreateOrganizationInput("총학생회", OrganizationType.STUDENT_COUNCIL, null,
			OrganizationScope.CAMPUS, school.getId(), false, null, null), OrganizationInputReason.COUNCIL_LEVEL_REQUIRED);
	}

	@Test
	@DisplayName("학생회가 아닌데 세부 구분이 있으면 COMMON_400 COUNCIL_LEVEL_NOT_ALLOWED다")
	void councilLevelNotAllowed() {
		assertInvalid(new CreateOrganizationInput("동아리", OrganizationType.CLUB, CouncilLevel.COLLEGE,
			OrganizationScope.CAMPUS, school.getId(), false, null, null),
			OrganizationInputReason.COUNCIL_LEVEL_NOT_ALLOWED);
	}

	@Test
	@DisplayName("연합인데 학교가 있으면 SCHOOL_NOT_ALLOWED, 교내인데 학교가 없으면 SCHOOL_REQUIRED다")
	void schoolRules() {
		assertInvalid(new CreateOrganizationInput("연합", OrganizationType.CLUB, null, OrganizationScope.UNION,
			school.getId(), false, null, null), OrganizationInputReason.SCHOOL_NOT_ALLOWED);
		assertInvalid(new CreateOrganizationInput("교내", OrganizationType.CLUB, null, OrganizationScope.CAMPUS,
			null, false, null, null), OrganizationInputReason.SCHOOL_REQUIRED);
	}

	@Test
	@DisplayName("없는 학교 id면 COMMON_400 SCHOOL_NOT_FOUND다")
	void schoolNotFound() {
		assertThatThrownBy(() -> organizationService.createOrganization(user.getId(), new CreateOrganizationInput(
			"교내", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, Long.MAX_VALUE, false, null, null)))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(OrganizationInputReason.SCHOOL_NOT_FOUND.name());
				assertThat(e.getContext()).containsEntry("schoolId", Long.MAX_VALUE);
			});
	}

	@Test
	@DisplayName("내 단체 목록은 내가 운영진으로 들어간 단체만 id 순으로 돌려준다")
	void getMyOrganizations() {
		Organization first = organizationService.createOrganization(user.getId(), SeedFixtures.createOrganizationInput());
		Organization second = organizationService.createOrganization(user.getId(), new CreateOrganizationInput(
			"두 번째", OrganizationType.ACADEMIC, null, OrganizationScope.CAMPUS, school.getId(), false, null, null));
		organizationService.createOrganization(other.getId(), SeedFixtures.createOrganizationInput());

		assertThat(organizationService.getMyOrganizations(user.getId()))
			.extracting(Organization::getId)
			.containsExactly(first.getId(), second.getId());
		assertThat(organizationService.getMyOrganizations(other.getId())).hasSize(1);
	}

	@Test
	@DisplayName("없는 단체를 조회하면 ORG_404다")
	void getOrganizationNotFound() {
		assertThatThrownBy(() -> organizationService.getOrganization(Long.MAX_VALUE))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
	}

	@Test
	@DisplayName("DB의 부분 unique index가 단체당 대표자 2명을 막는다")
	void representativeUniqueIndex() {
		Organization organization = organizationService.createOrganization(user.getId(),
			SeedFixtures.createOrganizationInput());

		assertThatThrownBy(() -> memberRepository.saveAndFlush(
			Member.create(organization, other, null, MemberRole.REPRESENTATIVE)))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("ORG_LOGO 용도로 발급받지 않은 fileKey면 COMMON_400을 던진다")
	void wrongPurposeFileKey() {
		assertThatThrownBy(() -> organizationService.updateLogo(1L, "posting-image/2026/01/a.png", user.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT));
	}

	@Test
	@DisplayName("로고를 바꿀 단체가 없으면 ORG_404를 던진다")
	void updateLogoOrganizationNotFound() {
		assertThatThrownBy(
			() -> organizationService.updateLogo(Long.MAX_VALUE, "org-logo/2026/01/a.png", user.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
	}

	@Test
	@DisplayName("그 단체의 운영진이 아니면 ORG_403을 던진다")
	void notMember() {
		Organization organization = organizationService.createOrganization(user.getId(),
			SeedFixtures.createOrganizationInput());

		assertThatThrownBy(
			() -> organizationService.updateLogo(organization.getId(), "org-logo/2026/01/a.png", other.getId()))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(OrganizationErrorCode.FORBIDDEN));
	}

	@Test
	@DisplayName("운영진이면 cdnDomain과 fileKey를 합쳐 logoUrl을 갱신한다")
	void updatesLogo() {
		Organization organization = organizationService.createOrganization(user.getId(),
			SeedFixtures.createOrganizationInput());

		Organization result = organizationService.updateLogo(organization.getId(), "org-logo/2026/01/a.png",
			user.getId());

		assertThat(result.getLogoUrl()).isEqualTo(s3Properties.cdnDomain() + "/org-logo/2026/01/a.png");
	}

	private void assertInvalid(CreateOrganizationInput input, OrganizationInputReason reason) {
		Consumer<BusinessException> check = e -> {
			assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
			assertThat(e.getReason()).isEqualTo(reason.name());
		};
		assertThatThrownBy(() -> organizationService.createOrganization(user.getId(), input))
			.isInstanceOfSatisfying(BusinessException.class, check);
	}

}
