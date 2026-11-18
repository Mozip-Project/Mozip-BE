package com.mozip.domain.organization.dto;

import java.util.List;

import com.mozip.domain.organization.entity.OrganizationField;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// 단체 프로필 수정 입력 (GraphQL UpdateOrganizationInput)
public record UpdateOrganizationInput(
	@Size(max = 500, message = "로고 주소는 500자 이하여야 합니다.") String logoUrl,
	@NotBlank(message = "한 줄 소개를 입력해 주세요") @Size(max = 40, message = "최대 40자까지 입력할 수 있어요") String tagline,
	@Size(max = 2000, message = "소개는 2000자 이하여야 합니다.") String description,
	@Email(message = "올바른 이메일 주소를 입력해 주세요") @Size(max = 255, message = "이메일은 255자 이하여야 합니다.") String email,
	@NotNull(message = "활동 분야 목록이 필요합니다.") List<OrganizationField> fields,
	@NotEmpty(message = "검색 키워드를 1개 이상 등록해 주세요")
	@Size(max = 10, message = "키워드는 최대 10개까지 등록 가능해요")
	List<@NotBlank(message = "빈 키워드는 등록할 수 없어요") @Size(max = 20, message = "키워드는 20자 이하여야 합니다.") String> keywords,
	@NotEmpty(message = "링크를 1개 이상 등록해 주세요")
	@Size(max = 10, message = "링크는 최대 10개까지 등록 가능해요")
	List<@NotBlank(message = "빈 링크는 등록할 수 없어요") @Size(max = 500, message = "링크는 500자 이하여야 합니다.") String> links
) {
}
