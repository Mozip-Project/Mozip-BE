package com.mozip.domain.application.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.application.dto.ApplicationInput;
import com.mozip.domain.application.entity.Answer;
import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.entity.ApplicationStatus;
import com.mozip.domain.application.entity.Attachment;
import com.mozip.domain.application.entity.DocResult;
import com.mozip.domain.application.entity.FinalResult;
import com.mozip.domain.application.entity.AttachmentType;
import com.mozip.domain.application.repository.AnswerRepository;
import com.mozip.domain.application.repository.AttachmentRepository;
import com.mozip.domain.application.service.ApplicationService;
import com.mozip.domain.file.service.FileService;
import com.mozip.domain.posting.entity.Position;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.PostingStatus;
import com.mozip.domain.posting.entity.Question;
import com.mozip.domain.posting.repository.PositionRepository;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.posting.repository.QuestionRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.AuthUser;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Controller
@RequiredArgsConstructor
public class ApplicationGraphqlController {

	private final ApplicationService applicationService;
	private final PostingRepository postingRepository;
	private final PositionRepository positionRepository;
	private final QuestionRepository questionRepository;
	private final UserRepository userRepository;
	private final AnswerRepository answerRepository;
	private final AttachmentRepository attachmentRepository;
	private final ObjectMapper objectMapper;
	private final FileService fileService;

	@MutationMapping
	public Application saveApplicationDraft(@Argument Long postingId, @Argument @Valid ApplicationInput input,
		@AuthenticationPrincipal AuthUser authUser) {
		return applicationService.saveDraft(authUser.userId(), postingId, input);
	}

	@MutationMapping
	public Application submitApplication(@Argument Long id, @AuthenticationPrincipal AuthUser authUser) {
		return applicationService.submit(authUser.userId(), id);
	}

	@QueryMapping
	public Application application(@Argument Long id, @AuthenticationPrincipal AuthUser authUser) {
		return applicationService.findDetail(id, authUser.userId());
	}

	@QueryMapping
	public List<Application> myApplications(@Argument ApplicationStatus status,
		@AuthenticationPrincipal AuthUser authUser) {
		return applicationService.findMyApplications(authUser.userId(), status);
	}

	@BatchMapping(typeName = "Application", field = "posting")
	public Map<Application, Posting> posting(List<Application> applications) {
		return groupToOne(applications, Application::getPosting, Posting::getId, postingRepository::findAllById);
	}

	@BatchMapping(typeName = "Application", field = "position")
	public Map<Application, Position> position(List<Application> applications) {
		return groupToOne(applications, Application::getPosition, Position::getId, positionRepository::findAllById);
	}

	@BatchMapping(typeName = "Application", field = "user")
	public Map<Application, User> user(List<Application> applications) {
		return groupToOne(applications, Application::getUser, User::getId, userRepository::findAllById);
	}

	@BatchMapping(typeName = "Application", field = "answers")
	public Map<Application, List<Answer>> answers(List<Application> applications) {
		return groupByParent(applications, Application::getId, answerRepository.findAllByApplicationInOrderById(applications),
			answer -> answer.getApplication().getId());
	}

	@BatchMapping(typeName = "Application", field = "docResult")
	public Map<Application, DocResult> docResult(List<Application> applications) {
		Map<Long, PostingStatus> statusByPostingId = postingStatusByPostingId(applications);
		return applications.stream().collect(Collectors.toMap(Function.identity(), application -> {
			PostingStatus status = statusByPostingId.get(application.getPosting().getId());
			return applicationService.resolveDocResult(application, status);
		}));
	}

	@BatchMapping(typeName = "Application", field = "finalResult")
	public Map<Application, FinalResult> finalResult(List<Application> applications) {
		Map<Long, PostingStatus> statusByPostingId = postingStatusByPostingId(applications);
		return applications.stream().collect(Collectors.toMap(Function.identity(), application -> {
			PostingStatus status = statusByPostingId.get(application.getPosting().getId());
			return applicationService.resolveFinalResult(application, status);
		}));
	}

	// JSON 문자열로 저장해 둔 걸 JSON 스칼라로 보낼 수 있게 파싱. 제출 전(null)이면 그대로 null
	@SchemaMapping(typeName = "Application", field = "profileSnapshot")
	public Map<String, Object> profileSnapshot(Application application) {
		if (application.getProfileSnapshot() == null) {
			return null;
		}
		return objectMapper.readValue(application.getProfileSnapshot(), Map.class);
	}

	@BatchMapping(typeName = "Answer", field = "question")
	public Map<Answer, Question> question(List<Answer> answers) {
		return groupToOne(answers, Answer::getQuestion, Question::getId, questionRepository::findAllById);
	}

	@BatchMapping(typeName = "Answer", field = "attachments")
	public Map<Answer, List<Attachment>> attachments(List<Answer> answers) {
		return groupByParent(answers, Answer::getId, attachmentRepository.findAllByAnswerInOrderById(answers),
			attachment -> attachment.getAnswer().getId());
	}

	// 지원서 조회 자체가 이미 지원자 본인/그 공고 운영진만 가능해서(application 쿼리, findDetail) 따로 권한 검사 안 함.
	// FILE은 url 컬럼에 fileKey를 저장해서 조회마다 presigned GET URL로 변환, LINK는 지원자가 입력한 링크 그대로 반환
	@BatchMapping(typeName = "Attachment", field = "url")
	public Map<Attachment, String> attachmentUrl(List<Attachment> attachments) {
		return attachments.stream().collect(HashMap::new, (map, attachment) -> map.put(attachment,
			attachment.getType() == AttachmentType.FILE ? fileService.presignGet(attachment.getUrl())
				: attachment.getUrl()), Map::putAll);
	}

	private Map<Long, PostingStatus> postingStatusByPostingId(List<Application> applications) {
		List<Long> postingIds = applications.stream().map(a -> a.getPosting().getId()).distinct().toList();
		return postingRepository.findAllById(postingIds).stream()
			.collect(Collectors.toMap(Posting::getId, Posting::getStatus));
	}

	// 자식의 부모 참조는 프록시일 수 있어 id로 묶어서, 결과 Map의 키는 GraphQL이 넘긴 부모 객체 그대로 사용
	private <T, C> Map<T, List<C>> groupByParent(List<T> parents, Function<T, Long> parentIdGetter, List<C> children,
		Function<C, Long> childParentIdGetter) {
		Map<Long, List<C>> childrenByParentId = children.stream()
			.collect(Collectors.groupingBy(childParentIdGetter));
		return parents.stream()
			.collect(Collectors.toMap(Function.identity(),
				parent -> childrenByParentId.getOrDefault(parentIdGetter.apply(parent), List.of())));
	}

	// null = 비공개/공통인 to-one 연관이라 Collectors.toMap 대신 직접 채워 넣음 (toMap은 값이 null이면 NPE)
	private <T, R> Map<T, R> groupToOne(List<T> items, Function<T, R> refGetter, Function<R, Long> idGetter,
		Function<List<Long>, List<R>> fetcher) {
		List<Long> ids = items.stream()
			.map(refGetter)
			.filter(ref -> ref != null)
			.map(idGetter)
			.distinct()
			.toList();
		Map<Long, R> byId = fetcher.apply(ids).stream()
			.collect(Collectors.toMap(idGetter, Function.identity()));
		Map<T, R> result = new HashMap<>();
		for (T item : items) {
			R ref = refGetter.apply(item);
			result.put(item, ref == null ? null : byId.get(idGetter.apply(ref)));
		}
		return result;
	}

}
