package com.mozip.domain.organization.dto;

// 팀 목록 저장 입력 한 줄 (GraphQL TeamInput). id가 있으면 기존 팀 수정, 없으면 새 팀. 이름 규칙은 TeamService에서 검사
public record TeamInput(Long id, String name) {
}
