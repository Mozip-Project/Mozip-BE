package com.mozip.domain.organization.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mozip.domain.organization.entity.Organization;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;
import com.mozip.domain.organization.exception.OrganizationErrorCode;
import com.mozip.domain.organization.repository.MemberRepository;
import com.mozip.domain.organization.repository.OrganizationRepository;
import com.mozip.global.config.S3Properties;
import com.mozip.global.exception.BusinessException;
import com.mozip.global.exception.CommonErrorCode;

class OrganizationServiceTest {

	private final OrganizationRepository organizationRepository = mock(OrganizationRepository.class);
	private final MemberRepository memberRepository = mock(MemberRepository.class);
	private final S3Properties s3Properties = new S3Properties("mozip-test", "ap-northeast-2", Duration.ofMinutes(10),
		"https://cdn.mozip.kr");
	private final OrganizationService organizationService = new OrganizationService(organizationRepository,
		memberRepository, s3Properties);

	@Test
	@DisplayName("ORG_LOGO 용도로 발급받지 않은 fileKey면 COMMON_400을 던진다")
	void wrongPurposeFileKey() {
		assertThatThrownBy(() -> organizationService.updateLogo(1L, "posting-image/2026/01/a.png", 1L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(CommonErrorCode.INVALID_INPUT);
	}

	@Test
	@DisplayName("단체가 없으면 ORG_404를 던진다")
	void organizationNotFound() {
		when(organizationRepository.findByIdWithSchool(1L)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> organizationService.updateLogo(1L, "org-logo/2026/01/a.png", 1L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(OrganizationErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("그 단체의 운영진이 아니면 ORG_403을 던진다")
	void notMember() {
		Organization organization = organization();
		when(organizationRepository.findByIdWithSchool(1L)).thenReturn(Optional.of(organization));
		when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(false);

		assertThatThrownBy(() -> organizationService.updateLogo(1L, "org-logo/2026/01/a.png", 1L))
			.isInstanceOf(BusinessException.class)
			.extracting(e -> ((BusinessException)e).getErrorCode())
			.isEqualTo(OrganizationErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("운영진이면 cdnDomain과 fileKey를 합쳐 logoUrl을 갱신한다")
	void updatesLogo() {
		Organization organization = organization();
		when(organizationRepository.findByIdWithSchool(1L)).thenReturn(Optional.of(organization));
		when(memberRepository.existsByOrganizationIdAndUserId(1L, 1L)).thenReturn(true);

		Organization result = organizationService.updateLogo(1L, "org-logo/2026/01/a.png", 1L);

		assertThat(result.getLogoUrl()).isEqualTo("https://cdn.mozip.kr/org-logo/2026/01/a.png");
	}

	private Organization organization() {
		return Organization.create("테스트 단체", OrganizationType.CLUB, OrganizationScope.CAMPUS, null, true);
	}

}
