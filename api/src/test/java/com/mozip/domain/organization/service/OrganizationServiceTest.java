package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.devtools.seed.SeedFixtures;
import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.dto.UpdateOrganizationInput;
import com.mozip.domain.organization.entity.CouncilLevel;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationField;
import com.mozip.domain.organization.entity.OrganizationFieldTag;
import com.mozip.domain.organization.entity.OrganizationKeyword;
import com.mozip.domain.organization.entity.OrganizationLink;
import com.mozip.domain.organization.entity.OrganizationLinkType;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.exception.OrganizationInputReason;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationFieldTagRepository;
import com.mozip.domain.organization.repository.OrganizationKeywordRepository;
import com.mozip.domain.organization.repository.OrganizationLinkRepository;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

// 테스트 메서드마다 트랜잭션을 열고 끝나면 롤백해 다른 테스트에 데이터가 남지 않음
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
	private OrganizationKeywordRepository keywordRepository;

	@Autowired
	private OrganizationFieldTagRepository fieldTagRepository;

	@Autowired
	private OrganizationLinkRepository linkRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

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
	@DisplayName("프로필을 수정하면 컬럼이 바뀌고 키워드/분야/링크는 보낸 목록으로 전체 교체된다")
	void updateOrganization() {
		Organization organization = organizationService.createOrganization(user.getId(),
			SeedFixtures.createOrganizationInput());

		organizationService.updateOrganization(user.getId(), organization.getId(), profileInput(
			List.of(OrganizationField.IT_DEV, OrganizationField.DESIGN_MEDIA, OrganizationField.IT_DEV),
			List.of(" 개발 ", "개발", "디자인"),
			List.of("instagram.com/mozip", "https://www.youtube.com/@mozip", "https://mozip.kr")));

		assertThat(organization.getTagline()).isEqualTo("한 줄 소개");
		assertThat(organization.getLogoUrl()).isEqualTo("https://cdn.example.com/logo.png");
		assertThat(organization.getEmail()).isEqualTo("club@example.com");
		assertThat(fieldTagRepository.findAllByOrganizationInOrderById(List.of(organization)))
			.extracting(OrganizationFieldTag::getField)
			.containsExactly(OrganizationField.IT_DEV, OrganizationField.DESIGN_MEDIA);
		assertThat(keywordRepository.findAllByOrganizationInOrderById(List.of(organization)))
			.extracting(OrganizationKeyword::getKeyword)
			.containsExactly("개발", "디자인");
		assertThat(linkRepository.findAllByOrganizationInOrderById(List.of(organization)))
			.extracting(OrganizationLink::getType, OrganizationLink::getUrl)
			.containsExactly(
				tuple(OrganizationLinkType.INSTAGRAM, "https://instagram.com/mozip"),
				tuple(OrganizationLinkType.YOUTUBE, "https://www.youtube.com/@mozip"),
				tuple(OrganizationLinkType.WEBSITE, "https://mozip.kr"));

		// 같은 키워드를 유지한 채 다시 저장해도 unique index에 걸리지 않고, 빠진 항목은 지워진다
		organizationService.updateOrganization(user.getId(), organization.getId(), profileInput(
			List.of(OrganizationField.SPORTS), List.of("개발"), List.of("https://mozip.kr")));
		keywordRepository.flush();

		assertThat(keywordRepository.findAllByOrganizationInOrderById(List.of(organization)))
			.extracting(OrganizationKeyword::getKeyword)
			.containsExactly("개발");
		assertThat(fieldTagRepository.findAllByOrganizationInOrderById(List.of(organization)))
			.extracting(OrganizationFieldTag::getField)
			.containsExactly(OrganizationField.SPORTS);
		assertThat(linkRepository.findAllByOrganizationInOrderById(List.of(organization))).hasSize(1);
	}

	@Test
	@DisplayName("단체 운영진이 아니면 프로필을 수정할 수 없다 (COMMON_403)")
	void updateOrganizationForbidden() {
		Organization organization = organizationService.createOrganization(user.getId(),
			SeedFixtures.createOrganizationInput());

		assertThatThrownBy(() -> organizationService.updateOrganization(other.getId(), organization.getId(),
			profileInput(List.of(OrganizationField.ETC), List.of("키워드"), List.of("https://mozip.kr"))))
			.isInstanceOfSatisfying(BusinessException.class,
				e -> assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.FORBIDDEN));
	}

	@Test
	@DisplayName("학생회가 아니면 활동 분야가 1개 이상이어야 하고, 학생회는 활동 분야를 넣을 수 없다")
	void fieldsRules() {
		Organization club = organizationService.createOrganization(user.getId(),
			SeedFixtures.createOrganizationInput());
		Organization council = organizationService.createOrganization(user.getId(), new CreateOrganizationInput(
			"총학생회", OrganizationType.STUDENT_COUNCIL, CouncilLevel.UNIVERSITY, OrganizationScope.CAMPUS,
			school.getId(), true, null, null));

		assertInvalidUpdate(club, profileInput(List.of(), List.of("키워드"), List.of("https://mozip.kr")),
			OrganizationInputReason.FIELDS_REQUIRED);
		assertInvalidUpdate(council,
			profileInput(List.of(OrganizationField.ETC), List.of("키워드"), List.of("https://mozip.kr")),
			OrganizationInputReason.FIELDS_NOT_ALLOWED);
	}

	@Test
	@DisplayName("호스트를 읽을 수 없거나 http, https가 아니거나 https://를 붙여 500자를 넘는 링크면 COMMON_400 LINK_URL_INVALID다")
	void linkUrlInvalid() {
		Organization organization = organizationService.createOrganization(user.getId(),
			SeedFixtures.createOrganizationInput());

		for (String link : List.of("https://", "javascript://example.com/%0aalert(1)", "ftp://example.com",
			"a".repeat(490) + ".com")) {
			assertInvalidUpdate(organization,
				profileInput(List.of(OrganizationField.ETC), List.of("키워드"), List.of(link)),
				OrganizationInputReason.LINK_URL_INVALID);
		}
	}

	@Test
	@DisplayName("빈 이메일은 null로 저장한다")
	void blankEmailBecomesNull() {
		Organization organization = organizationService.createOrganization(user.getId(),
			SeedFixtures.createOrganizationInput());

		organizationService.updateOrganization(user.getId(), organization.getId(), new UpdateOrganizationInput(
			null, "한 줄 소개", null, " ", List.of(OrganizationField.ETC), List.of("키워드"), List.of("https://mozip.kr")));

		assertThat(organization.getEmail()).isNull();
	}

	@Test
	@DisplayName("키워드 자동완성은 승인된 단체의 접두 일치만, 많이 쓰인 순으로 돌려주고 빈 접두어면 빈 목록이다")
	void keywordSuggestions() {
		Organization first = organizationService.createOrganization(user.getId(), SeedFixtures.createOrganizationInput());
		Organization second = organizationService.createOrganization(user.getId(), SeedFixtures.createOrganizationInput());
		Organization third = organizationService.createOrganization(other.getId(), SeedFixtures.createOrganizationInput());
		organizationService.updateOrganization(user.getId(), first.getId(),
			profileInput(List.of(OrganizationField.ETC), List.of("개발", "개발자", "디자인"), List.of("https://mozip.kr")));
		organizationService.updateOrganization(user.getId(), second.getId(),
			profileInput(List.of(OrganizationField.ETC), List.of("개발자", "디자인"), List.of("https://mozip.kr")));
		organizationService.updateOrganization(other.getId(), third.getId(),
			profileInput(List.of(OrganizationField.ETC), List.of("개발팀", "웹개발"), List.of("https://mozip.kr")));
		keywordRepository.flush();
		// 심사 승인 API가 아직 없어 상태만 직접 바꿈. third는 심사 전으로 남김
		jdbcTemplate.update("update organizations set review_status = 'APPROVED' where id in (?, ?)",
			first.getId(), second.getId());

		assertThat(organizationService.getKeywordSuggestions("개")).containsExactly("개발자", "개발");
		assertThat(organizationService.getKeywordSuggestions("개발자")).containsExactly("개발자");
		assertThat(organizationService.getKeywordSuggestions("%")).isEmpty();
		assertThat(organizationService.getKeywordSuggestions("  ")).isEmpty();
		assertThat(organizationService.getKeywordSuggestions("개".repeat(21))).isEmpty();
	}

	private static UpdateOrganizationInput profileInput(List<OrganizationField> fields, List<String> keywords,
		List<String> links) {
		return new UpdateOrganizationInput("https://cdn.example.com/logo.png", "한 줄 소개", "자세한 소개",
			"club@example.com", fields, keywords, links);
	}

	private void assertInvalidUpdate(Organization organization, UpdateOrganizationInput input,
		OrganizationInputReason reason) {
		assertThatThrownBy(() -> organizationService.updateOrganization(user.getId(), organization.getId(), input))
			.isInstanceOfSatisfying(BusinessException.class, e -> {
				assertThat(e.getErrorCode()).isEqualTo(CommonErrorCode.INVALID_INPUT);
				assertThat(e.getReason()).isEqualTo(reason.name());
			});
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
