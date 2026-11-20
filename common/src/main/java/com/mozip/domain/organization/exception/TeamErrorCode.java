package com.mozip.domain.organization.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 팀 관련 에러
@Getter
@RequiredArgsConstructor
public enum TeamErrorCode implements ErrorCode {

	// 공고 포지션이 참조하는 팀은 지우면 포지션의 팀 정보가 사라져 막음
	TEAM_IN_USE(HttpStatus.CONFLICT, "TEAM_409", "공고 포지션이 사용 중인 팀은 삭제할 수 없습니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
