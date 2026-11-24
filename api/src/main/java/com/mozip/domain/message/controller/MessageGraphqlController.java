package com.mozip.domain.message.controller;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.BatchMapping;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;

import com.mozip.domain.application.entity.Application;
import com.mozip.domain.application.repository.ApplicationRepository;
import com.mozip.domain.message.dto.ResultMessages;
import com.mozip.domain.message.entity.Message;
import com.mozip.domain.message.entity.MessageDelivery;
import com.mozip.domain.message.entity.MessageTarget;
import com.mozip.domain.message.entity.MessageType;
import com.mozip.domain.message.repository.MessageDeliveryRepository;
import com.mozip.domain.message.service.MessageService;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.repository.PostingRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class MessageGraphqlController {

	private final MessageService messageService;
	private final MessageDeliveryRepository messageDeliveryRepository;
	private final PostingRepository postingRepository;
	private final UserRepository userRepository;
	private final ApplicationRepository applicationRepository;

	@QueryMapping
	public ResultMessages resultMessages(@Argument Long postingId, @Argument MessageType type,
		@AuthenticationPrincipal AuthUser authUser) {
		return messageService.getResultMessages(authUser.userId(), postingId, type);
	}

	@MutationMapping
	public Message saveMessage(@Argument Long postingId, @Argument MessageType type, @Argument MessageTarget target,
		@Argument String subject, @Argument String body, @AuthenticationPrincipal AuthUser authUser) {
		return messageService.save(authUser.userId(), postingId, type, target, subject, body);
	}

	@MutationMapping
	public ResultMessages sendMessages(@Argument Long postingId, @Argument MessageType type,
		@Argument List<MessageTarget> targets, @AuthenticationPrincipal AuthUser authUser) {
		return messageService.send(authUser.userId(), postingId, type, targets);
	}

	@BatchMapping(typeName = "Message", field = "posting")
	public Map<Message, Posting> posting(List<Message> messages) {
		return groupToOne(messages, message -> message.getPosting().getId(), Posting::getId,
			postingRepository::findAllById);
	}

	@BatchMapping(typeName = "Message", field = "sender")
	public Map<Message, User> sender(List<Message> messages) {
		return groupToOne(messages, message -> message.getSender().getId(), User::getId, userRepository::findAllById);
	}

	@BatchMapping(typeName = "Message", field = "deliveries")
	public Map<Message, List<MessageDelivery>> deliveries(List<Message> messages) {
		Map<Long, List<MessageDelivery>> deliveriesByMessageId = messageDeliveryRepository
			.findAllByMessageInOrderById(messages)
			.stream()
			.collect(Collectors.groupingBy(delivery -> delivery.getMessage().getId()));
		return messages.stream()
			.collect(Collectors.toMap(Function.identity(),
				message -> deliveriesByMessageId.getOrDefault(message.getId(), List.of())));
	}

	@BatchMapping(typeName = "MessageDelivery", field = "application")
	public Map<MessageDelivery, Application> application(List<MessageDelivery> deliveries) {
		return groupToOne(deliveries, delivery -> delivery.getApplication().getId(), Application::getId,
			applicationRepository::findAllById);
	}

	// 부모가 들고 있는 연관은 프록시일 수 있어 id로 다시 읽고, 결과 Map의 키는 GraphQL이 넘긴 부모 객체 그대로 사용
	private static <T, R> Map<T, R> groupToOne(List<T> parents, Function<T, Long> refIdGetter,
		Function<R, Long> idGetter, Function<List<Long>, List<R>> fetcher) {
		Map<Long, R> byId = fetcher.apply(parents.stream().map(refIdGetter).distinct().toList()).stream()
			.collect(Collectors.toMap(idGetter, Function.identity()));
		return parents.stream()
			.collect(Collectors.toMap(Function.identity(), parent -> byId.get(refIdGetter.apply(parent))));
	}

}
