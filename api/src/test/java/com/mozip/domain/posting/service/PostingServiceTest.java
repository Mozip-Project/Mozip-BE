package com.mozip.domain.posting.service;

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
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.PostingFilter;
import com.mozip.domain.posting.dto.PostingPage;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingSort;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PostingServiceTest {

	@Autowired
	private PostingService postingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

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
	@DisplayName("publishAt 없이 게시하면 즉시 RECRUITING이 되고 publishedAt/bumpedAt이 찍힌다")
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
	@DisplayName("publishAt이 있으면 DRAFT로 두고 스케줄러에 맡긴다")
	void scheduledPublishStaysDraft() {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput("예약 게시용"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPublishAt(LocalDateTime.now().plusDays(1)));

		Posting result = postingService.publish(operator.getId(), posting.getId());

		assertThat(result.getStatus()).isEqualTo(PostingStatus.DRAFT);
		assertThat(result.getPublishedAt()).isNull();
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

	private Posting publishablePosting(String title) {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null, basicInput(title));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		return postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
	}

	// 1 title, 2 content, 3 semester, 4 region, 5 fee, 6 allowEnrolled, 7 allowOnLeave, 8 eligibilityNote,
	// 9 activityStartedOn, 10 activityEndedOn, 11 recruitStartAt, 12 recruitEndAt, 13 images,
	// 14 noPosition, 15 positions, 16 hasInterview, 17 interviewMethod, 18 interviewMinutes, 19 breakMinutes,
	// 20 assignRule, 21 maxApplicantsPerInterview, 22 maxParallelInterviews, 23 interviewTimeRanges,
	// 24 docScorePerQuestion, 25 questions, 26 publishAt, 27 representativeImageId
	private PostingDraftInput basicInput(String title) {
		return new PostingDraftInput(title, "본문", "2026-2", "서울", "무료", true, false, null,
			null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
			null, null,
			null, null, null, null, null, null, null, null,
			null, null,
			null, null);
	}

	private PostingDraftInput withPositions(List<PositionInput> positions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			false, positions,
			null, null, null, null, null, null, null, null,
			null, null,
			null, null);
	}

	private PostingDraftInput withQuestions(List<QuestionInput> questions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			true, questions,
			null, null);
	}

	private PostingDraftInput withPublishAt(LocalDateTime publishAt) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			null, null,
			publishAt, null);
	}

}
