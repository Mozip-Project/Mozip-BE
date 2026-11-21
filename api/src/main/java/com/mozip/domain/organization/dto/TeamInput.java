package com.mozip.domain.organization.dto;

// 팀 목록 저장 입력 한 줄, id가 있으면 기존 팀 수정이고 없으면 새 팀
public record TeamInput(Long id, String name) {
}
