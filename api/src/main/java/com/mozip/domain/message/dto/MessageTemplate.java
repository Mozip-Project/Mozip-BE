package com.mozip.domain.message.dto;

// 서버가 주는 기본 문구. 탭을 처음 열 때와 초기화에 프론트가 사용
public record MessageTemplate(String subject, String body) {
}
