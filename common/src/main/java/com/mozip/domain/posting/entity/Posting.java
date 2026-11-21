package com.mozip.domain.posting.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.mozip.domain.organization.entity.Organization;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 모집 공고
@Getter
@Entity
@Table(name = "postings")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Posting extends BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "organization_id", nullable = false)
	private Organization organization;

	// 이전 기수 불러오기로 만든 공고의 원본
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "source_posting_id")
	private Posting sourcePosting;

	@Column(nullable = false, length = 100)
	private String title;

	// 모집 학기 (예: 2026-2)
	@Column(length = 20)
	private String semester;

	// 모집 안내 (HTML)
	@Column(columnDefinition = "TEXT")
	private String content;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private PostingStatus status;

	// 작성 중 단계 1~5. DRAFT가 아니면 더 안 바뀜
	private Integer draftStep;

	@Column(nullable = false)
	private boolean hasInterview;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private InterviewMethod interviewMethod;

	@Column(nullable = false)
	private boolean noPosition;

	// null = 비공개
	@Column(length = 100)
	private String region;

	// null = 비공개
	@Column(length = 50)
	private String fee;

	@Column(nullable = false)
	private boolean allowEnrolled;

	@Column(nullable = false)
	private boolean allowOnLeave;

	// 지원 자격 직접 입력 (예: 1~3학년, 졸업 예정자 가능)
	@Column(length = 200)
	private String eligibilityNote;

	private LocalDate activityStartedOn;

	private LocalDate activityEndedOn;

	// 게시 예정 시각, 이 시각이 되면 DRAFT에서 RECRUITING으로 전환. null = 게시하기 누를 때 바로 게시
	private LocalDateTime publishAt;

	private LocalDateTime publishedAt;

	// 지원 접수 시작, 게시 후 이 시각 전까지는 공고만 보이고 지원 불가
	private LocalDateTime recruitStartAt;

	private LocalDateTime recruitEndAt;

	private LocalDateTime docResultAt;

	private LocalDateTime finalResultAt;

	// TODO(B 서류 평가): decideDocResult 메서드가 생기면 그쪽에서 채움. 지금은 컬럼만 둠
	private LocalDateTime docResultConfirmedAt;

	// TODO(B 면접): decideFinalResult 메서드가 생기면 그쪽에서 채움. 지금은 컬럼만 둠
	private LocalDateTime finalResultConfirmedAt;

	private Integer interviewMinutes;

	private Integer breakMinutes;

	@Enumerated(EnumType.STRING)
	@Column(length = 30)
	private AssignRule assignRule;

	private Integer maxApplicantsPerInterview;

	private Integer maxParallelInterviews;

	// 서류 평가에 문항별 점수(1~5점) 사용. 4단계 전에는 null
	private Boolean docScorePerQuestion;

	// 면접 평가에 질문별 점수 사용
	private Boolean interviewScorePerQuestion;

	// 지원자에게 보여줄 면접 안내 (준비물 등)
	@Column(columnDefinition = "TEXT")
	private String interviewGuide;

	// TODO(B 면접): 타임테이블 확정 메서드가 생기면 그쪽에서 채움. 지금은 컬럼만 둠
	private LocalDateTime timetableConfirmedAt;

	@Column(nullable = false)
	private int viewCount;

	// 최신순 정렬 기준 (게시, UP 시 갱신)
	private LocalDateTime bumpedAt;

	private Posting(Organization organization, String title) {
		this.organization = organization;
		this.title = title;
		this.status = PostingStatus.DRAFT;
		this.draftStep = 1;
		this.hasInterview = false;
		this.noPosition = false;
		this.allowEnrolled = true;
		this.allowOnLeave = false;
		this.viewCount = 0;
	}

	public static Posting create(Organization organization, String title) {
		return new Posting(organization, title);
	}

	// 1단계: 기본 정보
	public void updateBasicStep(String title, String content, String semester, String region, String fee,
		boolean allowEnrolled, boolean allowOnLeave, String eligibilityNote, LocalDate activityStartedOn,
		LocalDate activityEndedOn, LocalDateTime recruitStartAt, LocalDateTime recruitEndAt) {
		this.title = title;
		this.content = content;
		this.semester = semester;
		this.region = region;
		this.fee = fee;
		this.allowEnrolled = allowEnrolled;
		this.allowOnLeave = allowOnLeave;
		this.eligibilityNote = eligibilityNote;
		this.activityStartedOn = activityStartedOn;
		this.activityEndedOn = activityEndedOn;
		this.recruitStartAt = recruitStartAt;
		this.recruitEndAt = recruitEndAt;
		advanceDraftStep(1);
	}

	// 2단계: 포지션 (Position 목록 자체는 리포지토리에서 통째로 교체)
	public void updatePositionsStep(boolean noPosition) {
		this.noPosition = noPosition;
		advanceDraftStep(2);
	}

	// 3단계: 면접 (InterviewTimeRange 목록은 리포지토리에서 통째로 교체)
	public void updateInterviewStep(boolean hasInterview, InterviewMethod interviewMethod, Integer interviewMinutes,
		Integer breakMinutes, AssignRule assignRule, Integer maxApplicantsPerInterview,
		Integer maxParallelInterviews) {
		this.hasInterview = hasInterview;
		this.interviewMethod = hasInterview ? interviewMethod : null;
		this.interviewMinutes = hasInterview ? interviewMinutes : null;
		this.breakMinutes = hasInterview ? breakMinutes : null;
		this.assignRule = hasInterview ? assignRule : null;
		this.maxApplicantsPerInterview = hasInterview ? maxApplicantsPerInterview : null;
		this.maxParallelInterviews = hasInterview ? maxParallelInterviews : null;
		advanceDraftStep(3);
	}

	// 4단계: 문항 (Question 목록은 리포지토리에서 통째로 교체)
	public void updateQuestionsStep(boolean docScorePerQuestion) {
		this.docScorePerQuestion = docScorePerQuestion;
		advanceDraftStep(4);
	}

	// 5단계: 노출 (대표 이미지는 PostingImage 쪽에서 처리)
	public void updateExposureStep(LocalDateTime publishAt) {
		this.publishAt = publishAt;
		advanceDraftStep(5);
	}

	public void publishNow() {
		this.status = PostingStatus.RECRUITING;
		this.publishedAt = LocalDateTime.now();
		this.bumpedAt = LocalDateTime.now();
	}

	public boolean isDraft() {
		return status == PostingStatus.DRAFT;
	}

	// advancePostingStatus에서 씀. 순서·조건 검사는 호출하는 쪽(PostingService)에서 끝내고 넘어옴.
	// 스케줄러의 자동 전환은 조건부 UPDATE(PostingRepository)로 따로 처리해 이 메서드를 안 탐
	public void advanceStatus(PostingStatus to) {
		this.status = to;
		if (to == PostingStatus.DOC_RESULT_NOTICE) {
			this.docResultAt = LocalDateTime.now();
		} else if (to == PostingStatus.FINAL_RESULT) {
			this.finalResultAt = LocalDateTime.now();
		}
	}

	private void advanceDraftStep(int step) {
		if (draftStep == null || draftStep < step) {
			this.draftStep = step;
		}
	}

}
