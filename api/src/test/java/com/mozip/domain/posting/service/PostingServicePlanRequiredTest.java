package com.mozip.domain.posting.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.mozip.domain.organization.entity.Member;
import com.mozip.domain.organization.entity.MemberRole;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.domain.payment.service.SubscriptionService;
import com.mozip.domain.posting.dto.PositionInput;
import com.mozip.domain.posting.dto.PostingDraftInput;
import com.mozip.domain.posting.dto.QuestionInput;
import com.mozip.domain.posting.entity.Posting;
import com.mozip.domain.posting.entity.QuestionType;
import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.exception.BusinessException;

// hasActivePlan이 false인 경우(9단계 전에는 실제로 안 나지만 임시 구현을 목으로 뒤집어서 확인)
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PostingServicePlanRequiredTest {

	@Autowired
	private PostingService postingService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private MemberRepository memberRepository;

	@MockitoBean
	private SubscriptionService subscriptionService;

	private User operator;
	private Organization organization;

	@BeforeEach
	void setUp() {
		operator = userRepository.save(User.create("01033340001", "encoded", "운영진", false, LocalDateTime.now()));
		organization = organizationRepository.save(
			Organization.create("테스트 단체", OrganizationType.CLUB, null, OrganizationScope.CAMPUS, null, true, null,
				null));
		memberRepository.save(Member.create(organization, operator, null, MemberRole.REPRESENTATIVE));
	}

	@Test
	@DisplayName("단체에 이용권이 없으면 ORG_402를 던진다")
	void rejectsPublishingWithoutActivePlan() {
		when(subscriptionService.hasActivePlan(organization.getId())).thenReturn(false);

		Posting posting = postingService.saveDraft(operator.getId(), organization.getId(), null, basicInput("공고"));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withPositions(List.of(new PositionInput(null, "백엔드", 1, 0))));
		postingService.saveDraft(operator.getId(), organization.getId(), posting.getId(),
			withQuestions(List.of(new QuestionInput(null, QuestionType.TEXT, "자기소개", null, true, 500, null, 0))));

		assertThatThrownBy(() -> postingService.publish(operator.getId(), posting.getId()))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(OrganizationErrorCode.SUBSCRIPTION_REQUIRED);
	}

	private PostingDraftInput basicInput(String title) {
		return new PostingDraftInput(title, "본문", "2026-2", "서울", "무료", true, false, null,
			null, null, LocalDateTime.now(), LocalDateTime.now().plusDays(7), null,
			null, null,
			null, null, null, null, null, null, null, null,
			null, null,
			null, null);
	}

	private PostingDraftInput withPositions(List<PositionInput> positions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			false, positions,
			null, null, null, null, null, null, null, null,
			null, null,
			null, null);
	}

	private PostingDraftInput withQuestions(List<QuestionInput> questions) {
		return new PostingDraftInput(null, null, null, null, null, null, null, null,
			null, null, null, null, null,
			null, null,
			null, null, null, null, null, null, null, null,
			true, questions,
			null, null);
	}

}
