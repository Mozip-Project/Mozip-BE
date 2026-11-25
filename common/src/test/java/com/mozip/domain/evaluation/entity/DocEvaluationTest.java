package com.mozip.domain.evaluation.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.user.entity.User;

// 서류 평가의 상태 전환과 불변 조건 검증 (DB 없이)
class DocEvaluationTest {

	private final Organization organization = Organization.create("테스트 단체", OrganizationType.CLUB, null,
		OrganizationScope.UNION, null, false, null, null);
	private final User operator = User.create("01000000001", "encoded", "운영진", false, LocalDateTime.now());
	private final User applicant = User.create("01000000002", "encoded", "지원자", false, LocalDateTime.now());
	private final Member member = Member.create(organization, operator, null, MemberRole.REPRESENTATIVE);
	private final Application application = Application.create(Posting.create(organization, "공고"), null, applicant);

	@Test
	@DisplayName("새 평가는 DRAFT이고 의견과 제출 시각이 없으며 점수는 빈 목록이다")
	void createsDraft() {
		DocEvaluation evaluation = DocEvaluation.create(application, member);

		assertThat(evaluation.getStatus()).isEqualTo(DocEvaluationStatus.DRAFT);
		assertThat(evaluation.isSaved()).isFalse();
		assertThat(evaluation.getOpinion()).isNull();
		assertThat(evaluation.getSubmittedAt()).isNull();
		assertThat(evaluation.getScores()).isEmpty();
	}

	@Test
	@DisplayName("의견 없이 제출하면 거부되고 평가는 DRAFT로 남는다")
	void submitWithoutOpinion() {
		DocEvaluation evaluation = DocEvaluation.create(application, member);

		assertThatThrownBy(() -> evaluation.submit(null, List.of(), "메모", LocalDateTime.now()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(evaluation.getStatus()).isEqualTo(DocEvaluationStatus.DRAFT);
		assertThat(evaluation.getMemo()).isNull();
	}

	@Test
	@DisplayName("자동 저장은 값을 덮어쓰지만 DRAFT로 남고, 제출하면 SAVED가 되며 제출 시각이 남는다")
	void draftThenSubmit() {
		DocEvaluation evaluation = DocEvaluation.create(application, member);
		LocalDateTime now = LocalDateTime.of(2026, 11, 23, 10, 0);

		evaluation.saveDraft(null, List.of(new DocScore(1L, 3)), "작성 중");

		assertThat(evaluation.getStatus()).isEqualTo(DocEvaluationStatus.DRAFT);
		assertThat(evaluation.getScores()).containsExactly(new DocScore(1L, 3));
		assertThat(evaluation.getMemo()).isEqualTo("작성 중");
		assertThat(evaluation.getSubmittedAt()).isNull();

		evaluation.submit(Opinion.PASS, List.of(new DocScore(1L, 5)), "최종", now);

		assertThat(evaluation.getStatus()).isEqualTo(DocEvaluationStatus.SAVED);
		assertThat(evaluation.isSaved()).isTrue();
		assertThat(evaluation.getOpinion()).isEqualTo(Opinion.PASS);
		assertThat(evaluation.getScores()).containsExactly(new DocScore(1L, 5));
		assertThat(evaluation.getMemo()).isEqualTo("최종");
		assertThat(evaluation.getSubmittedAt()).isEqualTo(now);
	}

	@Test
	@DisplayName("SAVED인 평가를 자동 저장하면 값만 바뀌고 SAVED와 처음 제출 시각은 그대로다")
	void saveDraftAfterSubmitKeepsSaved() {
		DocEvaluation evaluation = DocEvaluation.create(application, member);
		LocalDateTime first = LocalDateTime.of(2026, 11, 23, 10, 0);
		evaluation.submit(Opinion.PASS, List.of(), null, first);

		evaluation.saveDraft(Opinion.HOLD, List.of(), "다시 생각");

		assertThat(evaluation.getStatus()).isEqualTo(DocEvaluationStatus.SAVED);
		assertThat(evaluation.getOpinion()).isEqualTo(Opinion.HOLD);
		assertThat(evaluation.getMemo()).isEqualTo("다시 생각");
		assertThat(evaluation.getSubmittedAt()).isEqualTo(first);
	}

	@Test
	@DisplayName("다시 제출해도 제출 시각은 처음 제출한 시각으로 남는다")
	void resubmitKeepsFirstSubmittedAt() {
		DocEvaluation evaluation = DocEvaluation.create(application, member);
		LocalDateTime first = LocalDateTime.of(2026, 11, 23, 10, 0);
		evaluation.submit(Opinion.PASS, List.of(), null, first);

		evaluation.submit(Opinion.FAIL, List.of(), null, first.plusHours(1));

		assertThat(evaluation.getOpinion()).isEqualTo(Opinion.FAIL);
		assertThat(evaluation.getSubmittedAt()).isEqualTo(first);
	}

	@Test
	@DisplayName("메모는 300자까지 되고 301자면 자동 저장과 제출 모두 거부된다")
	void memoLength() {
		DocEvaluation evaluation = DocEvaluation.create(application, member);

		evaluation.saveDraft(null, List.of(), "가".repeat(DocEvaluation.MEMO_MAX_LENGTH));
		assertThat(evaluation.getMemo()).hasSize(300);

		String tooLong = "가".repeat(DocEvaluation.MEMO_MAX_LENGTH + 1);
		assertThatThrownBy(() -> evaluation.saveDraft(null, List.of(), tooLong))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> evaluation.submit(Opinion.PASS, List.of(), tooLong, LocalDateTime.now()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(evaluation.getMemo()).hasSize(300);
		assertThat(evaluation.getStatus()).isEqualTo(DocEvaluationStatus.DRAFT);
	}

	@Test
	@DisplayName("점수 목록이 null이면 빈 목록으로 저장하고, 넘긴 목록을 나중에 바꿔도 평가에는 영향이 없다")
	void scoresAreCopied() {
		DocEvaluation evaluation = DocEvaluation.create(application, member);
		List<DocScore> scores = new ArrayList<>(List.of(new DocScore(1L, 4)));

		evaluation.saveDraft(null, scores, null);
		scores.add(new DocScore(2L, 2));

		assertThat(evaluation.getScores()).containsExactly(new DocScore(1L, 4));

		evaluation.saveDraft(null, null, null);

		assertThat(evaluation.getScores()).isEmpty();
	}

	@Test
	@DisplayName("점수는 1~5점만 되고 문항이 있어야 한다")
	void scoreRange() {
		assertThat(new DocScore(1L, 1).score()).isEqualTo(1);
		assertThat(new DocScore(1L, 5).score()).isEqualTo(5);
		assertThatThrownBy(() -> new DocScore(1L, 0)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DocScore(1L, 6)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new DocScore(null, 3)).isInstanceOf(IllegalArgumentException.class);
	}

}
