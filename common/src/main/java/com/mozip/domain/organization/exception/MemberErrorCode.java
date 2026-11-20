package com.mozip.domain.organization.exception;

import org.springframework.http.HttpStatus;

import com.mozip.global.exception.ErrorCode;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

// 운영진(소속) 관련 에러. 같은 코드 안의 상황 구분은 MemberReason
@Getter
@RequiredArgsConstructor
public enum MemberErrorCode implements ErrorCode {

	MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "MEMBER_404", "운영진을 찾을 수 없습니다."),
	MEMBER_CONFLICT(HttpStatus.CONFLICT, "MEMBER_409", "현재 운영진 상태에서는 처리할 수 없는 요청입니다."),
	// 초대(개인 초대, 공용 링크) 만료. 없는 초대 코드는 MEMBER_404
	INVITATION_EXPIRED(HttpStatus.GONE, "MEMBER_410", "만료된 초대입니다.");

	private final HttpStatus status;
	private final String code;
	private final String message;

}
