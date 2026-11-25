package com.mozip.domain.evaluation.entity;

import java.time.LocalDateTime;
import java.util.List;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.organization.entity.Member;
import com.mozip.global.common.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 운영진별 서류 평가
@Getter
@Entity
@Table(name = "doc_evaluations",
	uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "member_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DocEvaluation extends BaseEntity {

	public static final int MEMO_MAX_LENGTH = 300;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "application_id", nullable = false)
	private Application application;

	// 평가한 운영진
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private DocEvaluationStatus status;

	// 자동 저장만 된 작성 중 상태에서는 null
	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private Opinion opinion;

	// 운영진만 보는 메모
	@Column(length = MEMO_MAX_LENGTH)
	private String memo;

	// 문항별 점수, 점수를 안 쓰는 공고면 빈 목록
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private List<DocScore> scores;

	// 처음 SAVED가 된 시각, DRAFT면 null
	private LocalDateTime submittedAt;

	private DocEvaluation(Application application, Member member) {
		this.application = application;
		this.member = member;
		this.status = DocEvaluationStatus.DRAFT;
		this.scores = List.of();
	}

	public static DocEvaluation create(Application application, Member member) {
		return new DocEvaluation(application, member);
	}

	// 자동 저장. 폼 값 전체로 덮어쓰고 상태는 바꾸지 않아 SAVED인 평가는 SAVED로 남는다
	public void saveDraft(Opinion opinion, List<DocScore> scores, String memo) {
		overwrite(opinion, scores, memo);
	}

	// 저장 버튼. 의견이 있어야 하고 처음 제출한 시각만 남긴다
	public void submit(Opinion opinion, List<DocScore> scores, String memo, LocalDateTime now) {
		if (opinion == null) {
			throw new IllegalArgumentException("의견 없이 평가를 제출할 수 없습니다.");
		}
		overwrite(opinion, scores, memo);
		this.status = DocEvaluationStatus.SAVED;
		if (this.submittedAt == null) {
			this.submittedAt = now;
		}
	}

	public boolean isSaved() {
		return status == DocEvaluationStatus.SAVED;
	}

	private void overwrite(Opinion opinion, List<DocScore> scores, String memo) {
		if (memo != null && memo.length() > MEMO_MAX_LENGTH) {
			throw new IllegalArgumentException("메모는 300자를 넘을 수 없습니다.");
		}
		this.opinion = opinion;
		this.scores = scores == null ? List.of() : List.copyOf(scores);
		this.memo = memo;
	}

}
