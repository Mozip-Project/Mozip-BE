package com.mozip.domain.evaluation.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.application.service.ApplicationService;
import com.mozip.domain.evaluation.dto.ApplicationSort;
import com.mozip.domain.evaluation.dto.DocEvaluationApplicationPage;
import com.mozip.domain.evaluation.dto.DocEvaluationApplicationRow;
import com.mozip.domain.evaluation.dto.DocEvaluationTab;
import com.mozip.domain.evaluation.dto.DocEvaluationTabCounts;
import com.mozip.domain.evaluation.dto.DocScoreInput;
import com.mozip.domain.evaluation.dto.MyDocEvaluationStatus;
import com.mozip.domain.evaluation.dto.PositionCount;
import com.mozip.domain.evaluation.dto.SaveDocEvaluationInput;
import com.mozip.domain.evaluation.dto.SaveDocEvaluationPayload;
import com.mozip.domain.evaluation.entity.DocEvaluation;
import com.mozip.domain.evaluation.entity.DocScore;
import com.mozip.domain.evaluation.exception.DocEvaluationInputReason;
import com.mozip.domain.evaluation.exception.EvaluationErrorCode;
import com.mozip.domain.evaluation.repository.DocEvaluationRepository;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 서류 평가 목록과 작성. 평가자 지정 없이 공고 단체의 운영진이면 누구나 평가한다
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocEvaluationService {

	public static final int PAGE_SIZE = 10;

	private final DocEvaluationRepository docEvaluationRepository;
	private final ApplicationService applicationService;
	private final ApplicationRepository applicationRepository;
	private final PostingRepository postingRepository;
	private final PositionRepository positionRepository;
	private final QuestionRepository questionRepository;
	private final UserRepository userRepository;
	private final MemberRepository memberRepository;
	private final OrganizationAccessChecker organizationAccessChecker;

	public DocEvaluationApplicationPage findApplications(Long userId, Long postingId, DocEvaluationTab tab,
		List<Long> positionIds, String keyword, ApplicationSort sort, Integer page) {
		int pageNumber = page != null ? page : 1;
		if (pageNumber < 1) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("page", page);
		}
		Posting posting = getPostingAsOperator(userId, postingId);
		List<Position> positions = positionRepository.findAllByPostingOrderBySortOrder(posting);
		Set<Long> positionFilter = toPositionFilter(positions, positionIds);
		DocEvaluationTab effectiveTab = tab != null ? tab : DocEvaluationTab.ALL;

		List<Candidate> keywordMatched = loadCandidates(posting, userId).stream()
			.filter(candidate -> candidate.matchesKeyword(keyword))
			.toList();
		List<Candidate> positionMatched = keywordMatched.stream()
			.filter(candidate -> candidate.matchesPositions(positionFilter))
			.toList();
		List<Candidate> tabMatched = positionMatched.stream()
			.filter(candidate -> effectiveTab.includes(candidate.status()))
			.sorted(comparatorOf(sort))
			.toList();

		int totalCount = tabMatched.size();
		int totalPages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
		List<DocEvaluationApplicationRow> rows = tabMatched.stream()
			.skip((long)(pageNumber - 1) * PAGE_SIZE)
			.limit(PAGE_SIZE)
			.map(Candidate::toRow)
			.toList();
		return new DocEvaluationApplicationPage(rows, totalCount, countByTab(positionMatched),
			countByPosition(positions, keywordMatched), pageNumber, totalPages);
	}

	// 평가 화면 위쪽 칩. 목록과 같은 조건으로 걸러 쪽 나눔 없이 전부 돌려준다
	public List<DocEvaluationApplicationRow> findApplicationChips(Long userId, Long postingId, DocEvaluationTab tab,
		List<Long> positionIds, String keyword, ApplicationSort sort) {
		Posting posting = getPostingAsOperator(userId, postingId);
		Set<Long> positionFilter = toPositionFilter(positionRepository.findAllByPostingOrderBySortOrder(posting),
			positionIds);
		DocEvaluationTab effectiveTab = tab != null ? tab : DocEvaluationTab.ALL;

		return loadCandidates(posting, userId).stream()
			.filter(candidate -> candidate.matchesKeyword(keyword))
			.filter(candidate -> candidate.matchesPositions(positionFilter))
			.filter(candidate -> effectiveTab.includes(candidate.status()))
			.sorted(comparatorOf(sort))
			.map(Candidate::toRow)
			.toList();
	}

	// 아직 평가를 시작하지 않았으면 null
	public DocEvaluation findMyEvaluation(Long userId, Long applicationId) {
		Application application = getSubmittedApplication(applicationId);
		requireOperator(userId, application.getPosting().getOrganization().getId());
		return docEvaluationRepository.findByApplicationIdAndUserId(applicationId, userId).orElse(null);
	}

	// submit = false는 자동 저장(DRAFT), true는 저장 버튼(SAVED). 보낸 값으로 전체를 덮어쓴다
	@Transactional
	public SaveDocEvaluationPayload save(Long userId, SaveDocEvaluationInput input, boolean submit) {
		Application application = getSubmittedApplication(input.applicationId());
		Posting posting = application.getPosting();
		Long organizationId = posting.getOrganization().getId();
		requireOperator(userId, organizationId);
		if (posting.getDocResultConfirmedAt() != null) {
			throw new BusinessException(EvaluationErrorCode.RESULT_CONFIRMED).addContext("postingId", posting.getId());
		}
		List<DocScore> scores = validate(application, posting, input, submit);

		// 자동 저장과 저장 버튼이 겹치면 둘 다 새 행을 만들려다 unique 제약에 걸리므로 운영진 행을 잠가 순서대로 처리
		Member member = memberRepository.findForUpdateByOrganizationIdAndUserId(organizationId, userId)
			.orElseThrow(() -> new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("organizationId", organizationId));
		DocEvaluation evaluation = docEvaluationRepository
			.findByApplicationIdAndMemberId(application.getId(), member.getId())
			.orElseGet(() -> docEvaluationRepository.save(DocEvaluation.create(application, member)));
		if (submit) {
			evaluation.submit(input.opinion(), scores, input.memo(), LocalDateTime.now());
		} else {
			evaluation.saveDraft(input.opinion(), scores, input.memo());
		}
		// 응답의 updatedAt과 아래 남은 수 계산에 방금 저장한 값이 반영되도록 먼저 내보냄
		docEvaluationRepository.flush();

		List<Candidate> unevaluated = loadCandidates(posting, userId).stream()
			.filter(candidate -> candidate.status() != MyDocEvaluationStatus.SAVED)
			.toList();
		Long nextApplicationId = unevaluated.stream()
			.filter(candidate -> !candidate.application().getId().equals(application.getId()))
			.sorted(comparatorOf(ApplicationSort.SUBMITTED_DESC))
			.map(candidate -> candidate.application().getId())
			.findFirst()
			.orElse(null);
		return new SaveDocEvaluationPayload(evaluation, unevaluated.size(), nextApplicationId);
	}

	// 저장할 점수 목록을 돌려준다. 문항별 점수를 안 쓰는 공고면 점수를 보내도 저장하지 않는다
	private List<DocScore> validate(Application application, Posting posting, SaveDocEvaluationInput input,
		boolean submit) {
		if (input.memo() != null && input.memo().length() > DocEvaluation.MEMO_MAX_LENGTH) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.MEMO_TOO_LONG)
				.addContext("memoLength", input.memo().length());
		}
		if (submit && input.opinion() == null) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.OPINION_REQUIRED);
		}

		Set<Long> applicableQuestionIds = applicableQuestionIds(application, posting);
		List<DocScoreInput> scoreInputs = input.scores() != null ? input.scores() : List.of();
		List<DocScore> scores = new ArrayList<>();
		Set<Long> scoredQuestionIds = new HashSet<>();
		for (DocScoreInput scoreInput : scoreInputs) {
			if (scoreInput.score() < DocScore.MIN_SCORE || scoreInput.score() > DocScore.MAX_SCORE) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.INVALID_SCORE)
					.addContext("questionId", scoreInput.questionId())
					.addContext("score", scoreInput.score());
			}
			if (!applicableQuestionIds.contains(scoreInput.questionId())) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT,
					DocEvaluationInputReason.QUESTION_NOT_IN_POSTING)
					.addContext("questionId", scoreInput.questionId());
			}
			if (!scoredQuestionIds.add(scoreInput.questionId())) {
				throw new BusinessException(CommonErrorCode.INVALID_INPUT)
					.addContext("duplicatedQuestionId", scoreInput.questionId());
			}
			scores.add(new DocScore(scoreInput.questionId(), scoreInput.score()));
		}

		if (!Boolean.TRUE.equals(posting.getDocScorePerQuestion())) {
			return List.of();
		}
		if (submit && !scoredQuestionIds.containsAll(applicableQuestionIds)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT, DocEvaluationInputReason.SCORE_REQUIRED)
				.addContext("applicationId", application.getId());
		}
		return scores;
	}

	// 이 지원서가 답하는 문항. 공통 문항과 지원한 포지션의 문항
	private Set<Long> applicableQuestionIds(Application application, Posting posting) {
		Long positionId = application.getPosition() != null ? application.getPosition().getId() : null;
		return questionRepository.findAllByPostingOrderBySortOrder(posting).stream()
			.filter(question -> question.getPosition() == null
				|| question.getPosition().getId().equals(positionId))
			.map(Question::getId)
			.collect(Collectors.toSet());
	}

	// 공고의 제출된 지원서 전부에 지원자 이름과 내 평가 상태를 붙인다. 이름과 평가는 지원서마다 따로 조회하지 않고 한 번씩만 조회
	private List<Candidate> loadCandidates(Posting posting, Long userId) {
		List<Application> applications = applicationService.getSubmittedApplications(posting.getId());
		List<Long> applicantIds = applications.stream().map(application -> application.getUser().getId()).toList();
		Map<Long, String> nameByUserId = userRepository.findAllById(applicantIds).stream()
			.collect(Collectors.toMap(User::getId, User::getName));
		Map<Long, DocEvaluation> evaluationByApplicationId = docEvaluationRepository
			.findAllByPostingIdAndUserId(posting.getId(), userId).stream()
			.collect(Collectors.toMap(evaluation -> evaluation.getApplication().getId(), Function.identity()));
		return applications.stream()
			.map(application -> new Candidate(application,
				nameByUserId.getOrDefault(application.getUser().getId(), ""),
				MyDocEvaluationStatus.from(evaluationByApplicationId.get(application.getId()))))
			.toList();
	}

	private DocEvaluationTabCounts countByTab(List<Candidate> candidates) {
		int evaluated = (int)candidates.stream()
			.filter(candidate -> candidate.status() == MyDocEvaluationStatus.SAVED)
			.count();
		return new DocEvaluationTabCounts(candidates.size(), candidates.size() - evaluated, evaluated);
	}

	// 지원서가 없는 포지션도 0으로 넣어 필터 버튼이 검색어에 따라 사라지지 않게 함
	private List<PositionCount> countByPosition(List<Position> positions, List<Candidate> candidates) {
		Map<Long, Long> countByPositionId = candidates.stream()
			.filter(candidate -> candidate.application().getPosition() != null)
			.collect(Collectors.groupingBy(candidate -> candidate.application().getPosition().getId(),
				Collectors.counting()));
		return positions.stream()
			.map(position -> new PositionCount(position, countByPositionId.getOrDefault(position.getId(), 0L).intValue()))
			.toList();
	}

	// null = 포지션으로 거르지 않음
	private Set<Long> toPositionFilter(List<Position> positions, List<Long> positionIds) {
		if (positionIds == null || positionIds.isEmpty()) {
			return null;
		}
		Set<Long> postingPositionIds = positions.stream().map(Position::getId).collect(Collectors.toSet());
		if (!postingPositionIds.containsAll(positionIds)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("positionIds", positionIds);
		}
		return Set.copyOf(positionIds);
	}

	// 같은 값이면 id 순으로 정해 쪽을 넘겨도 순서가 흔들리지 않게 함
	private static Comparator<Candidate> comparatorOf(ApplicationSort sort) {
		Comparator<Candidate> bySubmittedAt = Comparator.comparing(
			candidate -> candidate.application().getSubmittedAt(), Comparator.nullsLast(Comparator.naturalOrder()));
		Comparator<Candidate> byName = Comparator.comparing(Candidate::name);
		Comparator<Candidate> byId = Comparator.comparing(candidate -> candidate.application().getId());
		return switch (sort != null ? sort : ApplicationSort.SUBMITTED_DESC) {
			case SUBMITTED_DESC -> bySubmittedAt.reversed().thenComparing(byId.reversed());
			case SUBMITTED_ASC -> bySubmittedAt.thenComparing(byId);
			case NAME_ASC -> byName.thenComparing(byId);
			case NAME_DESC -> byName.reversed().thenComparing(byId);
		};
	}

	private Posting getPostingAsOperator(Long userId, Long postingId) {
		Posting posting = postingRepository.findByIdWithOrganization(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		requireOperator(userId, posting.getOrganization().getId());
		return posting;
	}

	// 제출 전(DRAFT) 지원서는 평가 대상이 아니라 없는 것으로 본다
	private Application getSubmittedApplication(Long applicationId) {
		return applicationRepository.findById(applicationId)
			.filter(application -> !application.isDraft())
			.orElseThrow(() -> new BusinessException(EvaluationErrorCode.APPLICATION_NOT_FOUND)
				.addContext("applicationId", applicationId));
	}

	private void requireOperator(Long userId, Long organizationId) {
		if (!organizationAccessChecker.isOperatorOf(userId, organizationId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("organizationId", organizationId);
		}
	}

	// 목록 한 줄을 만들기 전 단계. 검색과 정렬에 쓰는 이름을 지원서 옆에 들고 다닌다
	private record Candidate(Application application, String name, MyDocEvaluationStatus status) {

		boolean matchesKeyword(String keyword) {
			if (keyword == null || keyword.isBlank()) {
				return true;
			}
			return name.toLowerCase(Locale.ROOT).contains(keyword.trim().toLowerCase(Locale.ROOT));
		}

		boolean matchesPositions(Set<Long> positionIds) {
			if (positionIds == null) {
				return true;
			}
			return application.getPosition() != null && positionIds.contains(application.getPosition().getId());
		}

		DocEvaluationApplicationRow toRow() {
			return new DocEvaluationApplicationRow(application, status);
		}

	}

}
