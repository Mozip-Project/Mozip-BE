package com.mozip.global.security;

import java.util.List;

// CORS 허용 요청 헤더를 추가로 등록할 때 구현 (예: 인증 우회 중에만 쓰는 X-Mock-Role)
public interface CorsAllowedHeadersContributor {

	List<String> allowedHeaders();

}
