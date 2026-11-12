package com.mozip.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

// SecurityConfig와 분리해 필터체인 설정이 바뀌어도 비밀번호 인코더를 쓰는 빈들이 순환 참조에 걸리지 않게 함
@Configuration
public class PasswordEncoderConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
