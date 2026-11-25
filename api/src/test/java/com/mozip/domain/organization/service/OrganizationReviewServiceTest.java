package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import com.mozip.TestcontainersConfiguration;
import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.entity.ReviewStatus;
import com.mozip.domain.organization.exception.OrganizationConflictReason;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.global.exception.BusinessException;

import jakarta.persistence.EntityManager;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class OrganizationReviewServiceTest {

	@Autowired
	private OrganizationReviewService reviewService;

	@Autowired
	private OrganizationRepository organizationRepository;

	@Autowired
	private EntityManager entityManager;

	private Long organizationId;

	@BeforeEach
	void setUp() {
		organizationId = organizationRepository.save(Organization.create("심사 대상 동아리", OrganizationType.CLUB, null,
			OrganizationScope.UNION, null, false, "https://example.com/proof", null)).getId();
	}

	@Test
	@DisplayName("심사 대기 중인 단체를 승인하면 APPROVED로 저장된다")
	void approve() {
		reviewService.approve(organizationId);

		Organization saved = reload();
		assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
		assertThat(saved.getRejectReason()).isNull();
	}

	@Test
	@DisplayName("심사 대기 중인 단체를 미승인하면 REJECTED와 사유가 저장된다")
	void reject() {
		reviewService.reject(organizationId, "증빙 자료가 확인되지 않습니다.");

		Organization saved = reload();
		assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
		assertThat(saved.getRejectReason()).isEqualTo("증빙 자료가 확인되지 않습니다.");
	}

	@Test
	@DisplayName("이미 승인된 단체를 미승인하려 하면 ORG_409 ALREADY_REVIEWED이고 상태는 그대로다")
	void rejectAfterApprove() {
		reviewService.approve(organizationId);

		assertThatThrownBy(() -> reviewService.reject(organizationId, "사유"))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException exception = (BusinessException)e;
				assertThat(exception.getErrorCode()).isEqualTo(OrganizationErrorCode.CONFLICT);
				assertThat(exception.getReason()).isEqualTo(OrganizationConflictReason.ALREADY_REVIEWED.name());
			});

		Organization saved = reload();
		assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.APPROVED);
		assertThat(saved.getRejectReason()).isNull();
	}

	@Test
	@DisplayName("이미 미승인된 단체를 승인하려 하면 ORG_409 ALREADY_REVIEWED이고 사유도 그대로다")
	void approveAfterReject() {
		reviewService.reject(organizationId, "처음 사유");

		assertThatThrownBy(() -> reviewService.approve(organizationId))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> {
				BusinessException exception = (BusinessException)e;
				assertThat(exception.getErrorCode()).isEqualTo(OrganizationErrorCode.CONFLICT);
				assertThat(exception.getReason()).isEqualTo(OrganizationConflictReason.ALREADY_REVIEWED.name());
			});

		Organization saved = reload();
		assertThat(saved.getReviewStatus()).isEqualTo(ReviewStatus.REJECTED);
		assertThat(saved.getRejectReason()).isEqualTo("처음 사유");
	}

	@Test
	@DisplayName("없는 단체를 심사하면 ORG_404다")
	void notFound() {
		assertThatThrownBy(() -> reviewService.approve(-1L))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> assertThat(((BusinessException)e).getErrorCode())
				.isEqualTo(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
		assertThatThrownBy(() -> reviewService.getReviewStatus(-1L))
			.isInstanceOf(BusinessException.class)
			.satisfies(e -> assertThat(((BusinessException)e).getErrorCode())
				.isEqualTo(OrganizationErrorCode.ORGANIZATION_NOT_FOUND));
	}

	// 영속성 컨텍스트를 비우고 DB에 저장된 값을 다시 읽음
	private Organization reload() {
		entityManager.flush();
		entityManager.clear();
		return organizationRepository.findById(organizationId).orElseThrow();
	}

}
