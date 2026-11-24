package com.mozip.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.evaluation.service.PostingResultChecker;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.posting.exception.PostingConflictReason;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;

// isDocResultConfirmed/isFinalResultConfirmed가 false인 경우를 목으로 뒤집어서 확인 (최종 결과는 아직 항상 true라
// 실제로는 안 남). PostingServicePlanRequiredTest와 같은 방식
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PostingServiceResultConfirmationTest {

	@Autowired
	private PostingService postingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@MockitoBean
	private PostingResultChecker postingResultChecker;

	private User operator;
	private Organization organization;

	@BeforeEach
	void setUp() {
		operator = userRepository.save(User.create("01033350001", "encoded", "운영진", false, LocalDateTime.now()));
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true, null,
				null));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
		when(postingResultChecker.isDocResultConfirmed(anyLong())).thenReturn(true);
		when(postingResultChecker.isFinalResultConfirmed(anyLong())).thenReturn(true);
	}

	@Test
	@DisplayName("서류 결과가 미확정이면 DOC_RESULT_NOTICE 전환에서 POSTING_409(DOC_RESULT_NOT_CONFIRMED)를 던진다")
	void rejectsDocResultNoticeWithoutConfirmation() {
		Posting posting = publishablePosting("서류 결과 확인용");
		postingService.publish(operator.getId(), posting.getId());
		postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_REVIEW);
		when(postingResultChecker.isDocResultConfirmed(posting.getId())).thenReturn(false);

		assertThatThrownBy(() -> postingService.advanceStatus(operator.getId(), posting.getId(),
			PostingStatus.DOC_RESULT_NOTICE))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(PostingErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(PostingConflictReason.DOC_RESULT_NOT_CONFIRMED.name());
			});
	}

	@Test
	@DisplayName("최종 결과가 미확정이면 FINAL_RESULT 전환에서 POSTING_409(FINAL_RESULT_NOT_CONFIRMED)를 던진다")
	void rejectsFinalResultWithoutConfirmation() {
		Posting posting = publishablePosting("최종 결과 확인용");
		postingService.publish(operator.getId(), posting.getId());
		postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_REVIEW);
		postingService.advanceStatus(operator.getId(), posting.getId(), PostingStatus.DOC_RESULT_NOTICE);
		when(postingResultChecker.isFinalResultConfirmed(posting.getId())).thenReturn(false);

		assertThatThrownBy(() -> postingService.advanceStatus(operator.getId(), posting.getId(),
			PostingStatus.FINAL_RESULT))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				assertThat(((BusinessException)e).getErrorCode()).isEqualTo(PostingErrorCode.CONFLICT);
				assertThat(((BusinessException)e).getReason()).isEqualTo(PostingConflictReason.FINAL_RESULT_NOT_CONFIRMED.name());
			});
	}

	private Posting publishablePosting(String title) {
		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null,
			basicInput(title));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		return postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));
	}

	private PostingDraftInput basicInput(String title) {
		return new PostingDraftInput(title, "본문", "2026-2", "서울", "무료", true, false, null,
			null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
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

	private PostingDraftInput withQuestions(List<QuestionInput> questions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			true, questions,
			null);
	}

}
