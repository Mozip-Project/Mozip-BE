package com.mozip.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

// @PreAuthorize 등 메서드 권한 검사 활성화
// auth.bypass.enabled=true면 로드하지 않음 (권한 검사 해제)
@Configuration
@EnableMethodSecurity
@ConditionalOnProperty(prefix = "auth.bypass", name = "enabled", havingValue = "false", matchIfMissing = true)
public class MethodSecurityConfig {
}
