package com.mozip.global.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import com.mozip.global.security.ConditionalOnAuthBypass;

// @PreAuthorize 등 메서드 권한 검사 활성화
// auth.bypass.enabled가 켜져 있으면 로드하지 않음 (devtools.authbypass 참고)
@Configuration
@EnableMethodSecurity
@ConditionalOnAuthBypass(enabled = false)
public class MethodSecurityConfig {
}
