package com.mozip.domain.evaluation.service;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.entity.DocResult;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.evaluation.dto.DocEvaluationSummaryPage;
import com.mozip.domain.evaluation.dto.DocEvaluationSummaryRow;
import com.mozip.domain.evaluation.dto.DocResultSummary;
import com.mozip.domain.evaluation.dto.DocResultTab;
import com.mozip.domain.evaluation.dto.DocResultTabCounts;
import com.mozip.domain.evaluation.dto.DocSummarySort;
import com.mozip.domain.evaluation.dto.PositionCount;
import com.mozip.domain.evaluation.entity.DocEvaluation;
import com.mozip.domain.evaluation.entity.DocEvaluationStatus;
import com.mozip.domain.evaluation.entity.DocScore;
import com.mozip.domain.evaluation.exception.EvaluationErrorCode;
import com.mozip.domain.evaluation.repository.DocEvaluationRepository;
import com.mozip.domain.evaluation.service.DocEvaluationSummaryCalculator.Applicant;
import com.mozip.domain.evaluation.service.DocEvaluationSummaryCalculator.SavedEvaluation;
import com.mozip.domain.evaluation.service.DocEvaluationSummaryCalculator.Stat;
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.service.OrganizationAccessChecker;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.exception.PostingErrorCode;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

import lombok.RequiredArgsConstructor;

// 서류 평가 취합 조회. 지원자별 평균 점수, 의견 수, 순위와 공고 전체 결과 현황을 계산함
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DocEvaluationSummaryService {

	public static final int PAGE_SIZE = 10;

	private final PostingRepository postingRepository;
	private final PositionRepository positionRepository;
	private final ApplicationRepository applicationRepository;
	private final UserRepository userRepository;
	private final MemberRepository memberRepository;
	private final DocEvaluationRepository docEvaluationRepository;
	private final OrganizationAccessChecker organizationAccessChecker;

	public DocEvaluationSummaryPage findSummary(Long userId, Long postingId, DocResultTab tab,
		List<Long> positionIds, String keyword, DocSummarySort sort, Integer page) {
		Posting posting = postingRepository.findByIdWithOrganization(postingId)
			.orElseThrow(() -> new BusinessException(PostingErrorCode.NOT_FOUND).addContext("postingId", postingId));
		requireOperator(userId, posting);

		int pageNumber = page == null ? 1 : page;
		if (pageNumber < 1) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT).addContext("page", page);
		}
		List<Position> positions = positionRepository.findAllByPostingOrderBySortOrder(posting);
		Set<Long> positionFilter = toPositionFilter(positions, positionIds, postingId);
		DocResultTab selectedTab = tab == null ? DocResultTab.ALL : tab;
		boolean useScore = usesScore(posting);

		List<DocEvaluationSummaryRow> allRows = loadRows(posting);
		// 이름 검색과 정렬에 쓰는 회원을 한 번에 불러 둠 (지원서의 user는 LAZY)
		userRepository.findAllById(allRows.stream().map(row -> row.application().getUser().getId()).toList());

		List<DocEvaluationSummaryRow> searched = allRows.stream()
			.filter(row -> matchesKeyword(row, keyword))
			.toList();
		List<DocEvaluationSummaryRow> positioned = searched.stream()
			.filter(row -> positionFilter.isEmpty() || positionFilter.contains(positionIdOf(row)))
			.toList();
		List<DocEvaluationSummaryRow> selected = positioned.stream()
			.filter(row -> selectedTab.matches(row.docResult()))
			.sorted(comparator(sort == null ? defaultSort(useScore) : sort))
			.toList();

		int totalCount = selected.size();
		int totalPages = (totalCount + PAGE_SIZE - 1) / PAGE_SIZE;
		// page가 아주 커도 넘치지 않게 long으로 계산
		long from = (long)(pageNumber - 1) * PAGE_SIZE;
		List<DocEvaluationSummaryRow> rows = from >= totalCount
			? List.of()
			: selected.subList((int)from, (int)Math.min(from + PAGE_SIZE, totalCount));

		return new DocEvaluationSummaryPage(rows, totalCount, tabCounts(positioned),
			positionCounts(positions, searched), pageNumber, totalPages, toSummary(posting, allRows));
	}

	// 지원서 한 건에 운영진이 저장한 평가 목록. 작성 중(DRAFT)인 평가는 본인만 보는 값이라 빼고 내려줌
	public List<DocEvaluation> findEvaluations(Long userId, Long applicationId) {
		Application application = applicationRepository.findById(applicationId)
			.orElseThrow(() -> applicationNotFound(applicationId));
		Posting posting = postingRepository.findByIdWithOrganization(application.getPosting().getId())
			.orElseThrow(() -> applicationNotFound(applicationId));
		requireOperator(userId, posting);
		if (application.isDraft()) {
			throw applicationNotFound(applicationId);
		}

		Set<Long> memberIds = memberIdsOf(posting);
		return docEvaluationRepository
			.findAllWithMemberByApplicationAndStatus(application, DocEvaluationStatus.SAVED).stream()
			.filter(evaluation -> memberIds.contains(evaluation.getMember().getId()))
			.toList();
	}

	// 공고 전체 결과 현황. 결과 지정과 확정 뒤 응답에도 같이 내려줌
	public DocResultSummary summarize(Posting posting) {
		return toSummary(posting, loadRows(posting));
	}

	// 공고 단체 운영진 전원이 이 지원서의 평가를 저장했는지
	public boolean isEvaluationComplete(Posting posting, Application application) {
		Set<Long> memberIds = memberIdsOf(posting);
		if (memberIds.isEmpty()) {
			return false;
		}
		Set<Long> savedMemberIds = docEvaluationRepository
			.findAllByApplicationInAndStatus(List.of(application), DocEvaluationStatus.SAVED).stream()
			.map(evaluation -> evaluation.getMember().getId())
			.collect(Collectors.toSet());
		return savedMemberIds.containsAll(memberIds);
	}

	// 제출된 지원서 전부를 계산해 한 줄씩 만듦. 순위가 공고 전체 기준이라 필터 전에 다 계산함
	private List<DocEvaluationSummaryRow> loadRows(Posting posting) {
		List<Application> applications = applicationRepository.findAllByPostingAndStatus(posting,
			ApplicationStatus.SUBMITTED);
		if (applications.isEmpty()) {
			return List.of();
		}
		Set<Long> memberIds = memberIdsOf(posting);
		// 단체를 나간 운영진의 평가는 세지 않음
		Map<Long, List<SavedEvaluation>> evaluationsByApplicationId = docEvaluationRepository
			.findAllByApplicationInAndStatus(applications, DocEvaluationStatus.SAVED).stream()
			.filter(evaluation -> memberIds.contains(evaluation.getMember().getId()))
			.collect(Collectors.groupingBy(evaluation -> evaluation.getApplication().getId(),
				Collectors.mapping(DocEvaluationSummaryService::toSavedEvaluation, Collectors.toList())));

		List<Applicant> applicants = applications.stream()
			.map(application -> new Applicant(application.getId(), application.getSubmittedAt(),
				evaluationsByApplicationId.getOrDefault(application.getId(), List.of())))
			.toList();
		Map<Long, Stat> stats = DocEvaluationSummaryCalculator.calculate(applicants, memberIds.size(),
			usesScore(posting));

		List<DocEvaluationSummaryRow> rows = new ArrayList<>();
		for (Application application : applications) {
			Stat stat = stats.get(application.getId());
			rows.add(new DocEvaluationSummaryRow(application, application.getDocResult(), stat.rank(),
				stat.averageScore(), stat.opinionCounts(), stat.evaluationComplete()));
		}
		return rows;
	}

	private static SavedEvaluation toSavedEvaluation(DocEvaluation evaluation) {
		return new SavedEvaluation(evaluation.getOpinion(),
			evaluation.getScores().stream().map(DocScore::score).toList());
	}

	private DocResultSummary toSummary(Posting posting, List<DocEvaluationSummaryRow> rows) {
		int totalCount = rows.size();
		int evaluatedCount = (int)rows.stream().filter(DocEvaluationSummaryRow::evaluationComplete).count();
		int passCount = countOf(rows, DocResult.PASS);
		int holdCount = countOf(rows, DocResult.HOLD);
		int failCount = countOf(rows, DocResult.FAIL);
		int pendingCount = countOf(rows, DocResult.PENDING);
		boolean allEvaluated = evaluatedCount == totalCount;
		boolean canConfirm = totalCount > 0 && allEvaluated && holdCount == 0 && pendingCount == 0
			&& !posting.isDocResultConfirmed();
		return new DocResultSummary(totalCount, evaluatedCount, allEvaluated, passCount, holdCount, failCount,
			pendingCount, canConfirm, posting.getDocResultConfirmedAt());
	}

	private DocResultTabCounts tabCounts(List<DocEvaluationSummaryRow> rows) {
		return new DocResultTabCounts(rows.size(), countOf(rows, DocResult.PASS), countOf(rows, DocResult.HOLD),
			countOf(rows, DocResult.FAIL));
	}

	private List<PositionCount> positionCounts(List<Position> positions,
		List<DocEvaluationSummaryRow> rows) {
		Map<Long, Long> countByPositionId = rows.stream()
			.filter(row -> positionIdOf(row) != null)
			.collect(Collectors.groupingBy(DocEvaluationSummaryService::positionIdOf, Collectors.counting()));
		return positions.stream()
			.map(position -> new PositionCount(position,
				countByPositionId.getOrDefault(position.getId(), 0L).intValue()))
			.toList();
	}

	private static int countOf(List<DocEvaluationSummaryRow> rows, DocResult docResult) {
		return (int)rows.stream().filter(row -> row.docResult() == docResult).count();
	}

	private static Long positionIdOf(DocEvaluationSummaryRow row) {
		Position position = row.application().getPosition();
		return position == null ? null : position.getId();
	}

	// 지원자 이름에 검색어가 들어 있는지. 검색어가 비면 전부 통과
	private static boolean matchesKeyword(DocEvaluationSummaryRow row, String keyword) {
		if (keyword == null || keyword.isBlank()) {
			return true;
		}
		String name = row.application().getUser().getName();
		return name.toLowerCase(Locale.ROOT).contains(keyword.strip().toLowerCase(Locale.ROOT));
	}

	// 비어 있으면 포지션 필터 없음. 이 공고의 포지션이 아닌 id가 섞여 있으면 거부
	private static Set<Long> toPositionFilter(List<Position> positions, List<Long> positionIds, Long postingId) {
		if (positionIds == null || positionIds.isEmpty()) {
			return Set.of();
		}
		Set<Long> validIds = positions.stream().map(Position::getId).collect(Collectors.toSet());
		Set<Long> filter = new HashSet<>(positionIds);
		if (!validIds.containsAll(filter)) {
			throw new BusinessException(CommonErrorCode.INVALID_INPUT)
				.addContext("postingId", postingId)
				.addContext("positionIds", positionIds);
		}
		return filter;
	}

	private static DocSummarySort defaultSort(boolean useScore) {
		return useScore ? DocSummarySort.SCORE_DESC : DocSummarySort.PASS_OPINION_DESC;
	}

	// 값이 같을 때 페이지마다 순서가 흔들리지 않게 마지막은 항상 지원서 id
	private static Comparator<DocEvaluationSummaryRow> comparator(DocSummarySort sort) {
		Comparator<DocEvaluationSummaryRow> byPassOpinion = Comparator
			.comparing((DocEvaluationSummaryRow row) -> row.opinionCounts().pass(), Comparator.reverseOrder())
			.thenComparing(row -> row.application().getSubmittedAt(),
				Comparator.nullsLast(Comparator.naturalOrder()));
		Collator collator = Collator.getInstance(Locale.KOREAN);
		Comparator<DocEvaluationSummaryRow> byName = Comparator
			.comparing((DocEvaluationSummaryRow row) -> row.application().getUser().getName(), collator);
		Comparator<DocEvaluationSummaryRow> primary = switch (sort) {
			case SCORE_DESC -> Comparator
				.comparing(DocEvaluationSummaryRow::averageScore,
					Comparator.nullsLast(Comparator.<Double>reverseOrder()))
				.thenComparing(byPassOpinion);
			case SCORE_ASC -> Comparator
				.comparing(DocEvaluationSummaryRow::averageScore,
					Comparator.nullsLast(Comparator.<Double>naturalOrder()))
				.thenComparing(byPassOpinion);
			case NAME_ASC -> byName;
			case NAME_DESC -> byName.reversed();
			case PASS_OPINION_DESC -> byPassOpinion;
		};
		return primary.thenComparing(row -> row.application().getId());
	}

	private Set<Long> memberIdsOf(Posting posting) {
		return memberRepository.findAllWithUserAndTeamByOrganizationIn(List.of(posting.getOrganization())).stream()
			.map(Member::getId)
			.collect(Collectors.toSet());
	}

	private static boolean usesScore(Posting posting) {
		return Boolean.TRUE.equals(posting.getDocScorePerQuestion());
	}

	private void requireOperator(Long userId, Posting posting) {
		Long organizationId = posting.getOrganization().getId();
		if (!organizationAccessChecker.isOperatorOf(userId, organizationId)) {
			throw new BusinessException(CommonErrorCode.FORBIDDEN)
				.addContext("userId", userId)
				.addContext("organizationId", organizationId);
		}
	}

	private static BusinessException applicationNotFound(Long applicationId) {
		return new BusinessException(EvaluationErrorCode.APPLICATION_NOT_FOUND)
			.addContext("applicationId", applicationId);
	}

}
