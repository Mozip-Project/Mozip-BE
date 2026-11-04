package com.mozip.global.health;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mozip.global.common.response.ApiResponse;

@RestController
public class HealthController implements HealthControllerApi {

	@Override
	@GetMapping("/api/health")
	public ApiResponse<HealthResponse> health() {
		return ApiResponse.ok(HealthResponse.up());
	}

}
