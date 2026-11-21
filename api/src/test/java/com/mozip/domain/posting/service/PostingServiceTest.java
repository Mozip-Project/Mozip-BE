package com.mozip.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.dto.InterviewTimeRangeInput;
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.PostingFilter;
import com.mozip.domain.posting.dto.PostingImageInput;
import com.mozip.domain.posting.dto.PostingPage;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.dto.QuestionOptionsInput;
import com.mozip.domain.posting.entity.AssignRule;
import com.mozip.domain.posting.entity.InterviewMethod;
import com.mozip.domain.posting.entity.InterviewTimeRange;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingImage;
import com.mozip.domain.posting.entity.PostingSort;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.exception.PostingConflictReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.InterviewTimeRangeRepository;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingImageRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PostingServiceTest {

	@Autowired
	private PostingService postingService;

	@Autowired
	private PostingRepository postingRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private PositionRepository positionRepository;

	@Autowired
	private QuestionRepository questionRepository;

	@Autowired
	private PostingImageRepository postingImageRepository;

	@Autowired
	private InterviewTimeRangeRepository interviewTimeRangeRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	@Autowired
	private EntityManager entityManager;

	private User operator;
	private User other;
	private Organization organization;

	@BeforeEach
	void setUp() {
		operator = userRepository.save(User.create("01033330001", "encoded", "운영진", false, LocalDateTime.now()));
		other = userRepository.save(User.create("01033330002", "encoded", "다른회원", false, LocalDateTime.now()));
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, OrganizationScope.CAMPUS, null, true));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
	}

	@Test
	@DisplayName("id 없이 savePostingDraft를 호출하면 새 DRAFT를 만든다")
	void createsNewDraft() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("백엔드 신입 모집"));

		assertThat(posting.getId()).isNotNull();
		assertThat(posting.getStatus()).isEqualTo(PostingStatus.DRAFT);
		assertThat(posting.getDraftStep()).isEqualTo(1);
		assertThat(posting.getTitle()).isEqualTo("백엔드 신입 모집");
	}

	@Test
	@DisplayName("단계별로 보낸 필드만 반영되고, 보내지 않은 단계의 필드는 유지된다")
	void partialSavePreservesOtherSteps() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("제목1"));

		Posting updated = postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));

		assertThat(updated.getTitle()).isEqualTo("제목1");
		assertThat(updated.isNoPosition()).isFalse();
		assertThat(updated.getDraftStep()).isEqualTo(2);
	}

	@Test
	@DisplayName("문항이 참조하는 포지션을 다시 저장해도 FK 에러 없이 그 문항은 공통 문항으로 떨어진다")
	void reSavingPositionsNullsOutQuestionReferenceInsteadOfFailing() {
		// 실제로는 단계마다 별도 요청(별도 트랜잭션/영속성 컨텍스트)이라 flush+clear로 그 경계를 흉내냄
		Long postingId = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("제목")).getId();
		flushAndClear();

		postingService.saveDraft(operator.getId(), organization.getId(), postingId,
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		flushAndClear();

		Long positionId = positionRepository.findAllByPostingOrderBySortOrder(
			entityManager.getReference(Posting.class, postingId)).get(0).getId();
		flushAndClear();

		postingService.saveDraft(operator.getId(), organization.getId(), postingId,
			withQuestions(List.of(
				new QuestionInput(positionId, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		flushAndClear();

		postingService.saveDraft(operator.getId(), organization.getId(), postingId,
			withPositions(List.of(new PositionInput(null, "프론트", 1, 0))));
		flushAndClear();

		List<Question> questions = questionRepository.findAllByPostingOrderBySortOrder(
			entityManager.getReference(Posting.class, postingId));
		assertThat(questions).hasSize(1);
		assertThat(questions.get(0).getPosition()).isNull();
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	@Test
	@DisplayName("대표 이미지를 다시 고르면 이전 대표는 내려가고 새로 고른 것만 대표로 남는다")
	void reMarkingRepresentativeImageMovesTheFlag() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("제목"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withImages(List.of(new PostingImageInput("https://img/a.png", 0),
				new PostingImageInput("https://img/b.png", 1))));
		List<PostingImage> images = postingImageRepository.findAllByPostingOrderBySortOrder(posting);
		Long firstId = images.get(0).getId();
		Long secondId = images.get(1).getId();

		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withRepresentativeImage(firstId));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withRepresentativeImage(secondId));

		List<PostingImage> updated = postingImageRepository.findAllByPostingOrderBySortOrder(posting);
		assertThat(updated).filteredOn(PostingImage::isRepresentative)
			.extracting(PostingImage::getId)
			.containsExactly(secondId);
	}

	@Test
	@DisplayName("1단계 이미지를 다시 저장해도 같은 url의 대표 지정은 유지된다")
	void resavingImagesPreservesRepresentativeByUrl() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("제목"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withImages(List.of(new PostingImageInput("https://img/a.png", 0))));
		Long imageId = postingImageRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withRepresentativeImage(imageId));

		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withImages(List.of(new PostingImageInput("https://img/a.png", 0),
				new PostingImageInput("https://img/c.png", 1))));

		List<PostingImage> images = postingImageRepository.findAllByPostingOrderBySortOrder(posting);
		assertThat(images).filteredOn(PostingImage::isRepresentative)
			.extracting(PostingImage::getUrl)
			.containsExactly("https://img/a.png");
	}

	@Test
	@DisplayName("noPosition을 true로만 보내도(positions 없이) 이전에 저장된 포지션이 지워진다")
	void settingNoPositionClearsExistingPositions() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("제목"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));

		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(), withNoPosition(true));

		List<Position> positions = positionRepository.findAllByPostingOrderBySortOrder(posting);
		assertThat(positions).isEmpty();
	}

	@Test
	@DisplayName("hasInterview을 false로만 보내도(interviewTimeRanges 없이) 이전에 저장된 면접 시간대가 지워진다")
	void settingHasInterviewFalseClearsExistingTimeRanges() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("제목"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		Long positionId = positionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withInterview(true, InterviewMethod.ONLINE, 20, 5, AssignRule.SAME_TEAM, 1, 2,
				List.of(new InterviewTimeRangeInput(positionId, LocalDate.now().plusDays(3), LocalTime.of(10, 0),
					LocalTime.of(12, 0), null))));

		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withInterview(false, null, null, null, null, null, null, null));

		List<InterviewTimeRange> ranges = interviewTimeRangeRepository
			.findAllByPostingOrderByDateAscStartTimeAsc(posting);
		assertThat(ranges).isEmpty();
	}

	@Test
	@DisplayName("DRAFT가 아닌 공고를 수정하면 POSTING_409를 던진다")
	void rejectsEditingNonDraft() {
		Posting posting = publishablePosting("게시용 공고");
		postingService.publish(operator.getId(), posting.getId());

		assertThatThrownBy(() -> postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			basicInput("수정")))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(PostingErrorCode.CONFLICT);
	}

	@Test
	@DisplayName("운영진이 아니면 COMMON_403을 던진다")
	void rejectsNonOperator() {
		assertThatThrownBy(() -> postingService.saveDraft(other.getId(), organization.getId(), null,
			basicInput("제목")))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("게시하면 즉시 RECRUITING이 되고 publishedAt/bumpedAt이 찍힌다")
	void publishesImmediately() {
		Posting posting = publishablePosting("게시용 공고");

		Posting published = postingService.publish(operator.getId(), posting.getId());

		assertThat(published.getStatus()).isEqualTo(PostingStatus.RECRUITING);
		assertThat(published.getPublishedAt()).isNotNull();
		assertThat(published.getBumpedAt()).isNotNull();
	}

	@Test
	@DisplayName("필수 단계(포지션, 문항)가 비어 있으면 POSTING_400을 던지고 fieldErrors에 단계가 담긴다")
	void rejectsPublishingWithMissingSteps() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("제목만"));

		assertThatThrownBy(() -> postingService.publish(operator.getId(), posting.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(PostingErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("이미 게시된 공고를 다시 게시하면 POSTING_409를 던진다")
	void rejectsPublishingAlreadyPublished() {
		Posting posting = publishablePosting("게시용 공고");
		postingService.publish(operator.getId(), posting.getId());

		assertThatThrownBy(() -> postingService.publish(operator.getId(), posting.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(PostingErrorCode.CONFLICT);
	}

	@Test
	@DisplayName("DRAFT는 그 단체 운영진만 조회할 수 있고, 아니면 POSTING_404로 숨긴다")
	void draftVisibleOnlyToOperator() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("비공개"));

		Posting found = postingService.findDetail(posting.getId(), operator.getId());
		assertThat(found.getId()).isEqualTo(posting.getId());

		assertThatThrownBy(() -> postingService.findDetail(posting.getId(), other.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(PostingErrorCode.NOT_FOUND);

		assertThatThrownBy(() -> postingService.findDetail(posting.getId(), null))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(PostingErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("postings는 RECRUITING만, 키워드 필터가 적용된 결과를 반환한다")
	void listsRecruitingPostingsWithFilter() {
		Posting backend = publishablePosting("백엔드 모집");
		postingService.publish(operator.getId(), backend.getId());
		Posting frontend = publishablePosting("프론트 모집");
		postingService.publish(operator.getId(), frontend.getId());
		// DRAFT는 안 보여야 함
		postingService.saveDraft(operator.getId(), organization.getId(), null, basicInput("아직 비공개"));

		PostingFilter filter = new PostingFilter("백엔드", null, null, null, null);
		PostingPage page = postingService.findList(filter, PostingSort.LATEST, 0, 10);

		assertThat(page.items()).extracting(Posting::getTitle).containsExactly("백엔드 모집");
		assertThat(page.totalCount()).isEqualTo(1);
		assertThat(page.hasNext()).isFalse();
	}

	@Test
	@DisplayName("postings의 size가 범위를 벗어나면 COMMON_400을 던진다")
	void rejectsInvalidPageSize() {
		assertThatThrownBy(() -> postingService.findList(null, PostingSort.LATEST, 0, 0))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("organizationPostings는 운영진만 조회할 수 있고 draftStep을 포함한다")
	void organizationPostingsRequiresOperator() {
		postingService.saveDraft(operator.getId(), organization.getId(), null, basicInput("단체 공고"));

		List<Posting> result = postingService.findOrganizationPostings(operator.getId(), organization.getId(), null);
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getDraftStep()).isEqualTo(1);

		assertThatThrownBy(
			() -> postingService.findOrganizationPostings(other.getId(), organization.getId(), null))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("postings는 schoolId와 organizationType 필터가 각각 적용된 결과만 반환한다")
	void listsPostingsFilteredBySchoolAndOrganizationType() {
		School schoolA = schoolRepository.save(School.create("A대", null));
		Organization clubAtSchoolA = organizationRepository.save(
			Organization.create("A단체(동아리)", OrganizationType.CLUB, OrganizationScope.CAMPUS, schoolA, true));
		Organization councilElsewhere = organizationRepository.save(
			Organization.create("타교 학생회", OrganizationType.STUDENT_COUNCIL, OrganizationScope.CAMPUS, null, true));
		memberRepository.save(Member.create(clubAtSchoolA, operator, null, MemberRole.REPRESENTATIVE));
		memberRepository.save(Member.create(councilElsewhere, operator, null, MemberRole.REPRESENTATIVE));
		postingService.publish(operator.getId(),
			publishablePosting(clubAtSchoolA, "A동아리 모집", LocalDateTime.now().plusDays(7)).getId());
		postingService.publish(operator.getId(),
			publishablePosting(councilElsewhere, "타교 학생회 모집", LocalDateTime.now().plusDays(7)).getId());

		PostingPage bySchool = postingService.findList(new PostingFilter(null, schoolA.getId(), null, null, null),
			PostingSort.LATEST, 0, 10);
		assertThat(bySchool.items()).extracting(Posting::getTitle).containsExactly("A동아리 모집");

		PostingPage byType = postingService.findList(
			new PostingFilter(null, null, OrganizationType.STUDENT_COUNCIL, null, null), PostingSort.LATEST, 0, 10);
		assertThat(byType.items()).extracting(Posting::getTitle).containsExactly("타교 학생회 모집");
	}

	@Test
	@DisplayName("postings는 hasInterview 필터가 적용된 결과만 반환한다")
	void listsPostingsFilteredByHasInterview() {
		Posting interviewPosting = publishablePosting("면접 있는 모집");
		postingService.saveDraft(operator.getId(), organization.getId(), interviewPosting.getId(),
			withInterview(true, InterviewMethod.ONLINE, 20, 5, AssignRule.ALL_MEMBERS, null, null, null));
		postingService.publish(operator.getId(), interviewPosting.getId());
		postingService.publish(operator.getId(), publishablePosting("면접 없는 모집").getId());

		PostingPage page = postingService.findList(new PostingFilter(null, null, null, true, null),
			PostingSort.LATEST, 0, 10);

		assertThat(page.items()).extracting(Posting::getTitle).containsExactly("면접 있는 모집");
	}

	@Test
	@DisplayName("postings는 closingSoon 필터와 CLOSING_SOON 정렬이 마감 임박 순으로 동작한다")
	void filtersAndSortsByClosingSoon() {
		postingService.publish(operator.getId(),
			publishablePosting("마감 임박", LocalDateTime.now().plusHours(1)).getId());
		postingService.publish(operator.getId(),
			publishablePosting("마감 여유", LocalDateTime.now().plusDays(10)).getId());

		PostingPage closingSoonOnly = postingService.findList(new PostingFilter(null, null, null, null, true),
			PostingSort.LATEST, 0, 10);
		assertThat(closingSoonOnly.items()).extracting(Posting::getTitle).containsExactly("마감 임박");

		PostingPage sorted = postingService.findList(null, PostingSort.CLOSING_SOON, 0, 10);
		assertThat(sorted.items()).extracting(Posting::getTitle).containsExactly("마감 임박", "마감 여유");
	}

	@Test
	@DisplayName("postings는 POPULAR 정렬에서 조회수가 높은 순으로 반환한다")
	void sortsByPopularity() {
		Posting lessPopular = publishablePosting("덜 인기있는 모집");
		postingService.publish(operator.getId(), lessPopular.getId());
		Posting morePopular = publishablePosting("더 인기있는 모집");
		postingService.publish(operator.getId(), morePopular.getId());

		entityManager.createQuery("UPDATE Posting p SET p.viewCount = :v WHERE p.id = :id")
			.setParameter("v", 100)
			.setParameter("id", morePopular.getId())
			.executeUpdate();
		flushAndClear();

		PostingPage page = postingService.findList(null, PostingSort.POPULAR, 0, 10);

		assertThat(page.items()).extracting(Posting::getTitle)
			.containsExactly("더 인기있는 모집", "덜 인기있는 모집");
	}

	@Test
	@DisplayName("postings는 size보다 결과가 많으면 hasNext가 true다")
	void paginatesWithHasNextTrue() {
		postingService.publish(operator.getId(), publishablePosting("모집1").getId());
		postingService.publish(operator.getId(), publishablePosting("모집2").getId());
		postingService.publish(operator.getId(), publishablePosting("모집3").getId());

		PostingPage page = postingService.findList(null, PostingSort.LATEST, 0, 2);

		assertThat(page.items()).hasSize(2);
		assertThat(page.totalCount()).isEqualTo(3);
		assertThat(page.hasNext()).isTrue();
	}

	@Test
	@DisplayName("organizationPostings는 status를 넘기면 그 상태의 공고만 반환한다")
	void organizationPostingsFiltersByStatus() {
		Posting draft = postingService.saveDraft(operator.getId(), organization.getId(), null, basicInput("드래프트"));
		Posting published = publishablePosting("게시됨");
		postingService.publish(operator.getId(), published.getId());

		List<Posting> draftsOnly = postingService.findOrganizationPostings(operator.getId(), organization.getId(),
			List.of(PostingStatus.DRAFT));

		assertThat(draftsOnly).extracting(Posting::getId).containsExactly(draft.getId());
	}

	@Test
	@DisplayName("면접이 있는데 면접 방식이 없으면 게시 시 POSTING_400을 던진다")
	void rejectsPublishingInterviewPostingWithoutMethod() {
		Posting posting = publishablePosting("면접 모집");
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withInterview(true, null, null, null, null, null, null, null));

		assertThatThrownBy(() -> postingService.publish(operator.getId(), posting.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(PostingErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("면접 단계를 저장하면 면접 가능 시간대가 포지션과 함께 저장된다")
	void savesInterviewTimeRanges() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("면접 시간 테스트"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		Long positionId = positionRepository.findAllByPostingOrderBySortOrder(posting).get(0).getId();

		LocalDate date = LocalDate.now().plusDays(3);
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withInterview(true, InterviewMethod.ONLINE, 20, 5, AssignRule.SAME_TEAM, 1, 2,
				List.of(new InterviewTimeRangeInput(positionId, date, LocalTime.of(10, 0), LocalTime.of(12, 0),
					null))));

		List<InterviewTimeRange> ranges = interviewTimeRangeRepository
			.findAllByPostingOrderByDateAscStartTimeAsc(posting);
		assertThat(ranges).hasSize(1);
		assertThat(ranges.get(0).getStartTime()).isEqualTo(LocalTime.of(10, 0));
	}

	@Test
	@DisplayName("SINGLE_CHOICE 문항을 저장하면 보기(choices)가 함께 저장된다")
	void savesQuestionChoices() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("문항 테스트"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.SINGLE_CHOICE, "선호 분야", null, true, null,
				new QuestionOptionsInput(List.of("백엔드", "프론트"), null, null, null), 0))));

		List<Question> questions = questionRepository.findAllByPostingOrderBySortOrder(posting);
		assertThat(questions.get(0).getOptions().choices()).containsExactly("백엔드", "프론트");
	}

	@Test
	@DisplayName("이미지를 6장 넘게 저장하면 COMMON_400을 던진다")
	void rejectsMoreThanFiveImages() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("이미지 테스트"));
		List<PostingImageInput> images = IntStream.range(0, 6)
			.mapToObj(i -> new PostingImageInput("https://img/" + i + ".png", i))
			.toList();

		assertThatThrownBy(() -> postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withImages(images)))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	private Posting publishablePosting(String title) {
		return publishablePosting(organization, title, LocalDateTime.now().plusDays(7));
	}

	private Posting publishablePosting(String title, LocalDateTime recruitEndAt) {
		return publishablePosting(organization, title, recruitEndAt);
	}

	private Posting publishablePosting(Organization org, String title, LocalDateTime recruitEndAt) {
		Posting posting = postingService.saveDraft(operator.getId(), org.getId(), null,
			basicInput(title, recruitEndAt));
		postingService.saveDraft(operator.getId(), org.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		return postingService.saveDraft(operator.getId(), org.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
	}

	// 1 title, 2 content, 3 semester, 4 region, 5 fee, 6 allowEnrolled, 7 allowOnLeave, 8 eligibilityNote,
	// 9 activityStartedOn, 10 activityEndedOn, 11 recruitStartAt, 12 recruitEndAt, 13 images,
	// 14 noPosition, 15 positions, 16 hasInterview, 17 interviewMethod, 18 interviewMinutes, 19 breakMinutes,
	// 20 assignRule, 21 maxApplicantsPerInterview, 22 maxParallelInterviews, 23 interviewTimeRanges,
	// 24 docScorePerQuestion, 25 questions, 26 representativeImageId
	private PostingDraftInput basicInput(String title) {
		return basicInput(title, LocalDateTime.now().plusDays(7));
	}

	private PostingDraftInput basicInput(String title, LocalDateTime recruitEndAt) {
		return new PostingDraftInput(title, "본문", "2026-2", "서울", "무료", true, false, null,
			null, null, LocalDateTime.now(), recruitEndAt, null,
			null, null,
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withPositions(List<PositionInput> positions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			false, positions,
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withNoPosition(boolean noPosition) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			noPosition, null,
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withQuestions(List<QuestionInput> questions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			true, questions,
			null);
	}

	private PostingDraftInput withImages(List<PostingImageInput> images) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, images,
			null, null,
			null, null, null, null, null, null, null, null,
			null, null,
			null);
	}

	private PostingDraftInput withRepresentativeImage(Long imageId) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			null, null,
			imageId);
	}

	private PostingDraftInput withInterview(Boolean hasInterview, InterviewMethod interviewMethod,
		Integer interviewMinutes, Integer breakMinutes, AssignRule assignRule, Integer maxApplicantsPerInterview,
		Integer maxParallelInterviews, List<InterviewTimeRangeInput> interviewTimeRanges) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			hasInterview, interviewMethod, interviewMinutes, breakMinutes, assignRule, maxApplicantsPerInterview,
			maxParallelInterviews, interviewTimeRanges,
			null, null,
			null);
	}

	@Test
	@DisplayName("비면접 공고는 advancePostingStatus로 INTERVIEW를 건너뛰고 정해진 순서대로 전환된다")
	void advancesNonInterviewPostingSkippingInterview() {
		Posting posting = publishablePosting("비면접 공고");
		postingService.publish(operator.getId(), posting.getId());

		assertThat(postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_REVIEW)
			.getStatus()).isEqualTo(PostingStatus.DOC_REVIEW);
		Posting docResultNotice = postingService.advanceStatus(operator.getId(), posting.getId(),
			PostingStatus.DOC_RESULT_NOTICE);
		assertThat(docResultNotice.getStatus()).isEqualTo(PostingStatus.DOC_RESULT_NOTICE);
		assertThat(docResultNotice.getDocResultAt()).isNotNull();
		Posting finalResult = postingService.advanceStatus(operator.getId(), posting.getId(),
			PostingStatus.FINAL_RESULT);
		assertThat(finalResult.getStatus()).isEqualTo(PostingStatus.FINAL_RESULT);
		assertThat(finalResult.getFinalResultAt()).isNotNull();
		assertThat(postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.ENDED).getStatus())
			.isEqualTo(PostingStatus.ENDED);
	}

	@Test
	@DisplayName("면접 공고는 advancePostingStatus로 INTERVIEW 단계를 거쳐 전환된다")
	void advancesInterviewPostingThroughInterviewStep() {
		Posting posting = publishablePosting("면접 공고");
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withInterview(true, InterviewMethod.ONLINE, 20, 5, AssignRule.ALL_MEMBERS, null, null, null));
		postingService.publish(operator.getId(), posting.getId());
		postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_REVIEW);

		Posting intoInterview = postingService.advanceStatus(operator.getId(), posting.getId(),
			PostingStatus.DOC_RESULT_NOTICE);
		assertThat(intoInterview.getStatus()).isEqualTo(PostingStatus.DOC_RESULT_NOTICE);

		Posting interview = postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.INTERVIEW);
		assertThat(interview.getStatus()).isEqualTo(PostingStatus.INTERVIEW);
	}

	@Test
	@DisplayName("정해진 다음 단계가 아닌 to로 advancePostingStatus를 부르면 POSTING_409(INVALID_STATUS_TRANSITION)를 던진다")
	void rejectsSkippingStatusSequence() {
		Posting posting = publishablePosting("순서 위반 공고");
		postingService.publish(operator.getId(), posting.getId());

		assertThatThrownBy(() -> postingService.advanceStatus(operator.getId(), posting.getId(),
			PostingStatus.DOC_RESULT_NOTICE))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(PostingErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(PostingConflictReason.INVALID_STATUS_TRANSITION);
			});
	}

	@Test
	@DisplayName("DRAFT 공고에 advancePostingStatus를 부르면 POSTING_409(INVALID_STATUS_TRANSITION)를 던진다")
	void rejectsAdvancingDraftPosting() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("아직 작성 중"));

		assertThatThrownBy(() -> postingService.advanceStatus(operator.getId(), posting.getId(),
			PostingStatus.DOC_REVIEW))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(PostingErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(PostingConflictReason.INVALID_STATUS_TRANSITION);
			});
	}

	@Test
	@DisplayName("advancePostingStatus도 운영진이 아니면 COMMON_403을 던진다")
	void rejectsAdvancingByNonOperator() {
		Posting posting = publishablePosting("권한 확인용");
		postingService.publish(operator.getId(), posting.getId());

		assertThatThrownBy(() -> postingService.advanceStatus(other.getId(), posting.getId(),
			PostingStatus.DOC_REVIEW))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("deletePosting은 DRAFT 공고와 그 하위 포지션/문항을 같이 지운다")
	void deletesDraftPostingWithChildren() {
		Posting posting = publishablePosting("지울 공고");
		Long postingId = posting.getId();

		postingService.delete(operator.getId(), postingId);

		assertThat(postingRepository.findById(postingId)).isEmpty();
		assertThat(positionRepository.findAllByPostingOrderBySortOrder(posting)).isEmpty();
		assertThat(questionRepository.findAllByPostingOrderBySortOrder(posting)).isEmpty();
	}

	@Test
	@DisplayName("게시된 공고를 deletePosting하면 POSTING_409(NOT_DRAFT)를 던진다")
	void rejectsDeletingNonDraftPosting() {
		Posting posting = publishablePosting("삭제 거부용");
		postingService.publish(operator.getId(), posting.getId());

		assertThatThrownBy(() -> postingService.delete(operator.getId(), posting.getId()))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(PostingErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(PostingConflictReason.NOT_DRAFT);
			});
	}

	@Test
	@DisplayName("getPostingStatus는 공고의 현재 상태를 돌려준다")
	void getPostingStatusReturnsCurrentStatus() {
		Posting posting = publishablePosting("상태 조회용");

		assertThat(postingService.getPostingStatus(posting.getId())).isEqualTo(PostingStatus.DRAFT);

		postingService.publish(operator.getId(), posting.getId());

		assertThat(postingService.getPostingStatus(posting.getId())).isEqualTo(PostingStatus.RECRUITING);
	}

}
