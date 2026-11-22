package com.mozip.domain.organization.dto;

import com.mozip.domain.organization.entity.CouncilLevel;
import com.mozip.domain.organization.entity.OrganizationScope;
import com.mozip.domain.organization.entity.OrganizationType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateOrganizationInput(
	@NotBlank(message = "단체 이름은 필수입니다.") @Size(max = 50, message = "단체 이름은 50자 이하여야 합니다.") String name,
	@NotNull(message = "단체 종류는 필수입니다.") OrganizationType type,
	CouncilLevel councilLevel,
	@NotNull(message = "교내/연합 구분은 필수입니다.") OrganizationScope scope,
	Long schoolId,
	boolean noTeam,
	// http, https 주소만 받음
	@Size(max = 500, message = "증빙 링크는 500자 이하여야 합니다.")
	@Pattern(regexp = "(?i)^https?://\\S+$", message = "증빙 링크는 http 또는 https 주소여야 합니다.") String proofLink,
	@Size(max = 500, message = "증빙 파일 주소는 500자 이하여야 합니다.")
	@Pattern(regexp = "(?i)^https?://\\S+$", message = "증빙 파일 주소는 http 또는 https 주소여야 합니다.") String proofFileUrl
) {
}
