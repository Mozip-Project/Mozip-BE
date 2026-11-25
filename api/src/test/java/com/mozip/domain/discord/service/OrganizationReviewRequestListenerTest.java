package com.mozip.domain.discord.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.organization.dto.CreateOrganizationInput;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.organization.service.OrganizationService;
import com.mozip.domain.school.entity.School;
import com.mozip.domain.school.repository.SchoolRepository;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;

// 단체 생성이 커밋된 뒤에만 리스너가 돌기 때문에 @Transactional을 붙이지 않음 (데이터가 남아 unique 값은 난수로)
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "discord.review-channel-id=review-channel")
class OrganizationReviewRequestListenerTest {

	@Autowired
	private OrganizationService organizationService;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SchoolRepository schoolRepository;

	@MockitoBean
	private DiscordClient discordClient;

	private User user;
	private School school;

	@BeforeEach
	void setUp() {
		String phone = "010" + ThreadLocalRandom.current().nextInt(10_000_000, 100_000_000);
		user = userRepository.save(User.create(phone, "encoded", "김대표", false, LocalDateTime.now()));
		school = schoolRepository.save(School.create("심사테스트대학교", null));
	}

	@Test
	@DisplayName("단체를 만들면 커밋 후 심사 채널에 단체 정보와 승인/미승인 버튼이 담긴 메시지를 보낸다")
	@SuppressWarnings("unchecked")
	void sendsReviewRequest() {
		Organization organization = organizationService.createOrganization(user.getId(), input());

		ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
		verify(discordClient).createMessage(eq("review-channel"), payload.capture());

		Map<String, Object> embed = ((List<Map<String, Object>>)payload.getValue().get("embeds")).getFirst();
		List<Map<String, Object>> fields = (List<Map<String, Object>>)embed.get("fields");
		assertThat(fields).extracting(field -> field.get("name"), field -> field.get("value"))
			.containsExactly(
				tuple("단체 이름", "심사 요청 동아리"),
				tuple("유형", "동아리"),
				tuple("범위", "교내"),
				tuple("학교", "심사테스트대학교"),
				tuple("대표자", "김대표 (회원 id " + user.getId() + ")"),
				tuple("증빙 링크", "https://example.com/proof"),
				tuple("증빙 파일", "없음"));

		Map<String, Object> row = ((List<Map<String, Object>>)payload.getValue().get("components")).getFirst();
		List<Map<String, Object>> buttons = (List<Map<String, Object>>)row.get("components");
		assertThat(buttons).extracting(button -> button.get("label"), button -> button.get("custom_id"))
			.containsExactly(
				tuple("승인", "org-review:approve:" + organization.getId()),
				tuple("미승인", "org-review:reject:" + organization.getId()));
		assertThat(payload.getValue().get("allowed_mentions")).isEqualTo(Map.of("parse", List.of()));
	}

	@Test
	@DisplayName("디스코드 클라이언트가 예외를 던져도 단체 생성은 성공하고 PENDING으로 저장된다")
	void discordFailureDoesNotFailCreation() {
		doThrow(new IllegalStateException("디스코드 장애")).when(discordClient).createMessage(anyString(), any());

		Organization organization = organizationService.createOrganization(user.getId(), input());

		verify(discordClient).createMessage(eq("review-channel"), any());
		assertThat(organizationRepository.findById(organization.getId()))
			.get()
			.extracting(Organization::getReviewStatus)
			.isEqualTo(ReviewStatus.PENDING);
		assertThat(memberRepository.existsByOrganizationIdAndUserIdAndRole(organization.getId(), user.getId(),
			MemberRole.REPRESENTATIVE)).isTrue();
	}

	@Test
	@DisplayName("단체 생성이 실패해 롤백되면 심사 요청을 보내지 않는다")
	void noMessageWhenCreationFails() {
		CreateOrganizationInput invalid = new CreateOrganizationInput("심사 요청 동아리", OrganizationType.CLUB, null,
			OrganizationScope.CAMPUS, -1L, false, null, null);

		assertThatThrownBy(() -> organizationService.createOrganization(user.getId(), invalid))
			.isInstanceOf(BusinessException.class);

		verifyNoInteractions(discordClient);
	}

	private CreateOrganizationInput input() {
		return new CreateOrganizationInput("심사 요청 동아리", OrganizationType.CLUB, null, OrganizationScope.CAMPUS,
			school.getId(), false, "https://example.com/proof", null);
	}

}
