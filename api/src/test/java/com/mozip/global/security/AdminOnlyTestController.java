package com.mozip.global.security;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mozip.global.common.response.ApiResponse;

// 테스트 전용 컨트롤러 (권한 어노테이션, 현재 사용자 확인용, @Import로만 등록)
@RestController
public class AdminOnlyTestController {

	public static final String PATH = "/test/admin-only";
	public static final String ME_PATH = "/test/me";

	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping(PATH)
	public ApiResponse<Void> adminOnly() {
		return ApiResponse.ok();
	}

	@GetMapping(ME_PATH)
	public ApiResponse<AuthUser> me(@AuthenticationPrincipal AuthUser authUser) {
		return ApiResponse.ok(authUser);
	}

}
