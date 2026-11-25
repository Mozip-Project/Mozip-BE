package com.mozip.domain.discord.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.discord.DiscordTestKeys;
import com.mozip.domain.discord.service.DiscordClient;
import com.mozip.domain.discord.service.LoggingDiscordClient;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.organization.service.OrganizationReviewNotifier;

// 디스코드가 보내는 것과 같은 형식의 요청을 테스트용 Ed25519 키로 서명해서 호출 (실제 디스코드와는 통신하지 않음)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class DiscordInteractionControllerTest {

	private static final DiscordTestKeys KEYS = new DiscordTestKeys();
	private static final String URL = "/api/discord/interactions";
	private static final String TIMESTAMP = "1732412345";
	private static final String ALLOWED_ROLE = "111";
	private static final String OTHER_ROLE = "333";
	private static final String ALLOWED_USER = "999";
	private static final String REVIEWER = "1001";
	private static final int UPDATE_MESSAGE = 7;
	private static final int CHANNEL_MESSAGE = 4;
	private static final int MODAL = 9;
	private static final int EPHEMERAL = 64;

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private DiscordClient discordClient;

	@MockitoSpyBean
	private OrganizationReviewNotifier reviewNotifier;

	private Long organizationId;

	@DynamicPropertySource
	static void discordProperties(DynamicPropertyRegistry registry) {
		registry.add("discord.public-key", KEYS::publicKeyHex);
		registry.add("discord.allowed-role-ids", () -> ALLOWED_ROLE + ", 222");
		registry.add("discord.allowed-user-ids", () -> ALLOWED_USER);
	}

	@BeforeEach
	void setUp() {
		organizationId = organizationRepository.save(Organization.create("심사 대상 동아리", OrganizationType.CLUB, null,
			OrganizationScope.UNION, null, false, "https://example.com/proof", null)).getId();
	}

	@Test
	@DisplayName("서명 헤더가 없으면 로그인 없이도 401 DISCORD_401로 응답한다")
	void missingSignature() throws Exception {
		mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{\"type\":1}"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("DISCORD_401"));
	}

	@Test
	@DisplayName("서명이 body와 맞지 않으면 401이고 단체 상태는 바뀌지 않는다")
	void invalidSignature() throws Exception {
		String signedBody = "{\"type\":1}";
		String sentBody = button("approve", REVIEWER, ALLOWED_ROLE);

		mockMvc.perform(post(URL)
				.contentType(MediaType.APPLICATION_JSON)
				.header(DiscordInteractionController.SIGNATURE_HEADER,
					KEYS.sign(TIMESTAMP, signedBody.getBytes(StandardCharsets.UTF_8)))
				.header(DiscordInteractionController.TIMESTAMP_HEADER, TIMESTAMP)
				.content(sentBody))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("DISCORD_401"));

		mockMvc.perform(post(URL)
				.contentType(MediaType.APPLICATION_JSON)
				.header(DiscordInteractionController.SIGNATURE_HEADER,
					new DiscordTestKeys().sign(TIMESTAMP, sentBody.getBytes(StandardCharsets.UTF_8)))
				.header(DiscordInteractionController.TIMESTAMP_HEADER, TIMESTAMP)
				.content(sentBody))
			.andExpect(status().isUnauthorized());

		assertThat(reviewStatus()).isEqualTo(ReviewStatus.PENDING);
	}

	@Test
	@DisplayName("PING(type 1)에는 PONG(type 1)만 응답한다")
	void ping() throws Exception {
		send("{\"type\":1}")
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value(1))
			.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("허용된 역할이 승인 버튼을 누르면 APPROVED로 바뀌고 원래 메시지가 결과로 갱신되며 버튼이 사라진다")
	void approve() throws Exception {
		send(button("approve", REVIEWER, ALLOWED_ROLE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value(UPDATE_MESSAGE))
			.andExpect(jsonPath("$.data.content", containsString("심사 완료: 승인")))
			.andExpect(jsonPath("$.data.content", containsString("처리자: <@" + REVIEWER + ">")))
			.andExpect(jsonPath("$.data.content", containsString("처리 시각: <t:")))
			.andExpect(jsonPath("$.data.components", hasSize(0)));

		assertThat(reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
		verify(reviewNotifier).notifyApproved(organizationId);
	}

	@Test
	@DisplayName("미승인 버튼을 누르면 사유 모달이 뜨고, 제출하면 REJECTED와 사유가 저장되고 메시지가 갱신된다")
	void reject() throws Exception {
		send(button("reject", REVIEWER, ALLOWED_ROLE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value(MODAL))
			.andExpect(jsonPath("$.data.custom_id").value("org-review:reject-modal:" + organizationId))
			.andExpect(jsonPath("$.data.components[0].components[0].custom_id").value("reason"))
			.andExpect(jsonPath("$.data.components[0].components[0].max_length").value(500));
		assertThat(reviewStatus()).isEqualTo(ReviewStatus.PENDING);

		send(modalSubmit("  증빙 자료가 확인되지 않습니다.  ", REVIEWER, ALLOWED_ROLE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value(UPDATE_MESSAGE))
			.andExpect(jsonPath("$.data.content", containsString("심사 완료: 미승인")))
			.andExpect(jsonPath("$.data.content", containsString("처리자: <@" + REVIEWER + ">")))
			.andExpect(jsonPath("$.data.content", containsString("사유: 증빙 자료가 확인되지 않습니다.")))
			.andExpect(jsonPath("$.data.components", hasSize(0)));

		Organization saved = organizationRepository.findById(organizationId).orElseThrow();
		assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
		assertThat(saved.getRejectReason()).isEqualTo("증빙 자료가 확인되지 않습니다.");
		verify(reviewNotifier).notifyRejected(organizationId, "증빙 자료가 확인되지 않습니다.");
	}

	@Test
	@DisplayName("사유가 공백뿐인 모달 제출은 ephemeral로 알리고 상태를 바꾸지 않는다")
	void rejectWithBlankReason() throws Exception {
		send(modalSubmit("   ", REVIEWER, ALLOWED_ROLE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value(CHANNEL_MESSAGE))
			.andExpect(jsonPath("$.data.flags").value(EPHEMERAL));

		assertThat(reviewStatus()).isEqualTo(ReviewStatus.PENDING);
	}

	@Test
	@DisplayName("이미 승인된 단체에 승인, 미승인, 사유 제출이 다시 오면 상태를 바꾸지 않고 ephemeral 메시지로 알린다")
	void alreadyReviewed() throws Exception {
		send(button("approve", REVIEWER, ALLOWED_ROLE)).andExpect(jsonPath("$.type").value(UPDATE_MESSAGE));

		for (String body : List.of(button("approve", "1002", ALLOWED_ROLE), button("reject", "1002", ALLOWED_ROLE),
			modalSubmit("늦게 도착한 사유", "1002", ALLOWED_ROLE))) {
			send(body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value(CHANNEL_MESSAGE))
				.andExpect(jsonPath("$.data.flags").value(EPHEMERAL))
				.andExpect(jsonPath("$.data.content", containsString("이미 처리된 단체입니다. 현재 상태: 승인")));
		}

		Organization saved = organizationRepository.findById(organizationId).orElseThrow();
		assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
		assertThat(saved.getRejectReason()).isNull();
		verify(reviewNotifier, never()).notifyRejected(anyLong(), any());
	}

	@Test
	@DisplayName("허용 목록에 없는 역할과 사용자는 버튼을 눌러도, 모달을 제출해도 ephemeral로 거부되고 상태는 PENDING이다")
	void forbidden() throws Exception {
		for (String body : List.of(button("approve", REVIEWER, OTHER_ROLE), button("reject", REVIEWER, OTHER_ROLE),
			modalSubmit("사유", REVIEWER, OTHER_ROLE))) {
			send(body)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.type").value(CHANNEL_MESSAGE))
				.andExpect(jsonPath("$.data.flags").value(EPHEMERAL))
				.andExpect(jsonPath("$.data.content").value("단체 심사 권한이 없습니다."));
		}

		assertThat(reviewStatus()).isEqualTo(ReviewStatus.PENDING);
		verify(reviewNotifier, never()).notifyApproved(anyLong());
	}

	@Test
	@DisplayName("역할이 없어도 허용된 사용자 id면 승인할 수 있다")
	void allowedUser() throws Exception {
		send(button("approve", ALLOWED_USER, OTHER_ROLE)).andExpect(jsonPath("$.type").value(UPDATE_MESSAGE));

		assertThat(reviewStatus()).isEqualTo(ReviewStatus.APPROVED);
	}

	@Test
	@DisplayName("없는 단체의 버튼이나 우리 형식이 아닌 custom_id는 ephemeral 메시지로 알린다")
	void unknownTarget() throws Exception {
		send(interaction(3, "{\"custom_id\":\"org-review:approve:-1\",\"component_type\":2}", REVIEWER, ALLOWED_ROLE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value(CHANNEL_MESSAGE))
			.andExpect(jsonPath("$.data.flags").value(EPHEMERAL))
			.andExpect(jsonPath("$.data.content", containsString("단체를 찾을 수 없습니다.")));

		send(interaction(3, "{\"custom_id\":\"something-else\",\"component_type\":2}", REVIEWER, ALLOWED_ROLE))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.type").value(CHANNEL_MESSAGE))
			.andExpect(jsonPath("$.data.flags").value(EPHEMERAL));
	}

	@Test
	@DisplayName("승인과 미승인이 동시에 들어와도 한쪽만 처리되고 다른 쪽은 이미 처리됨으로 응답한다")
	void concurrentReview() throws Exception {
		CountDownLatch start = new CountDownLatch(1);
		List<Callable<String>> requests = List.of(
			() -> responseOf(start, button("approve", REVIEWER, ALLOWED_ROLE)),
			() -> responseOf(start, modalSubmit("동시에 들어온 미승인", "1002", ALLOWED_ROLE)));

		List<String> responses;
		try (ExecutorService executor = Executors.newFixedThreadPool(requests.size())) {
			List<Future<String>> futures = requests.stream().map(executor::submit).toList();
			start.countDown();
			responses = List.of(futures.get(0).get(), futures.get(1).get());
		}

		assertThat(responses).filteredOn(response -> response.contains("\"type\":" + UPDATE_MESSAGE)).hasSize(1);
		assertThat(responses).filteredOn(response -> response.contains("이미 처리된 단체입니다.")).hasSize(1);

		Organization saved = organizationRepository.findById(organizationId).orElseThrow();
		boolean approved = responses.get(0).contains("\"type\":" + UPDATE_MESSAGE);
		assertThat(saved.getReviewStatus()).isEqualTo(approved ? ReviewStatus.APPROVED : ReviewStatus.REJECTED);
		assertThat(saved.getRejectReason()).isEqualTo(approved ? null : "동시에 들어온 미승인");
	}

	@Test
	@DisplayName("디스코드 설정이 없는 환경에서는 로그만 남기는 클라이언트가 등록된다")
	void loggingClientByDefault() {
		assertThat(discordClient).isInstanceOf(LoggingDiscordClient.class);
	}

	private ReviewStatus reviewStatus() {
		return organizationRepository.findById(organizationId).orElseThrow().getReviewStatus();
	}

	private String responseOf(CountDownLatch start, String body) throws Exception {
		start.await();
		return send(body).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	// 디스코드처럼 timestamp + body 원문에 서명해서 보냄
	private ResultActions send(String body) throws Exception {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		return mockMvc.perform(post(URL)
			.contentType(MediaType.APPLICATION_JSON)
			.header(DiscordInteractionController.SIGNATURE_HEADER, KEYS.sign(TIMESTAMP, bytes))
			.header(DiscordInteractionController.TIMESTAMP_HEADER, TIMESTAMP)
			.content(bytes));
	}

	private String button(String action, String userId, String roleId) {
		return interaction(3,
			"{\"custom_id\":\"org-review:" + action + ":" + organizationId + "\",\"component_type\":2}", userId, roleId);
	}

	private String modalSubmit(String reason, String userId, String roleId) {
		return interaction(5, """
			{"custom_id":"org-review:reject-modal:%d","components":[{"type":1,"components":[\
			{"type":4,"custom_id":"reason","value":"%s"}]}]}""".formatted(organizationId, reason), userId, roleId);
	}

	private static String interaction(int type, String data, String userId, String roleId) {
		return """
			{"id":"1","application_id":"2","type":%d,"token":"interaction-token","version":1,\
			"member":{"user":{"id":"%s","username":"reviewer"},"roles":["%s"]},"data":%s}"""
			.formatted(type, userId, roleId, data);
	}

}
