package com.mozip.domain.organization.dto;

import com.mozip.domain.organization.entity.CouncilLevel;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// 단체 생성 입력 (GraphQL CreateOrganizationInput), 필드 간 규칙은 OrganizationService에서 검사
public record CreateOrganizationInput(
	@NotBlank(message = "단체 이름은 필수입니다.") @Size(max = 50, message = "단체 이름은 50자 이하여야 합니다.") String name,
	@NotNull(message = "단체 종류는 필수입니다.") OrganizationType type,
	CouncilLevel councilLevel,
	@NotNull(message = "교내/연합 구분은 필수입니다.") OrganizationScope scope,
	Long schoolId,
	boolean noTeam,
	// 관리자 심사 화면에서 링크로 열리므로 javascript: 같은 주소가 저장되지 않게 http, https만 받음
	@Size(max = 500, message = "증빙 링크는 500자 이하여야 합니다.")
	@Pattern(regexp = "(?i)^https?://\\S+$", message = "증빙 링크는 http 또는 https 주소여야 합니다.") String proofLink,
	@Size(max = 500, message = "증빙 파일 주소는 500자 이하여야 합니다.")
	@Pattern(regexp = "(?i)^https?://\\S+$", message = "증빙 파일 주소는 http 또는 https 주소여야 합니다.") String proofFileUrl
) {
}
