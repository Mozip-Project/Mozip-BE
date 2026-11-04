package com.mozip.global.security.mock;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.AuthUser;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 인증 우회 중 앱 시작 시 역할별 개발용 회원 생성 (이미 있으면 재사용)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "auth.bypass", name = "enabled", havingValue = "true")
public class MockUserRegistry implements ApplicationRunner {

	// 개발용 회원 로그인 비밀번호
	public static final String DEV_PASSWORD = "mozip1234";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	private final Map<MockRole, AuthUser> users = new EnumMap<>(MockRole.class);

	@Override
	public void run(ApplicationArguments args) {
		for (MockRole role : MockRole.values()) {
			User user = userRepository.findByPhone(role.getPhone())
				.orElseGet(() -> userRepository.save(User.createDevUser(role.getPhone(),
					passwordEncoder.encode(DEV_PASSWORD), role.getUserName(), role.getAuthority(), role.getLastRole(),
					LocalDateTime.now())));
			users.put(role, new AuthUser(user.getId(), user.getAuthority()));
			log.warn("개발용 회원 준비: role={}, userId={}, phone={}", role, user.getId(), role.getPhone());
		}
	}

	public AuthUser get(MockRole role) {
		AuthUser authUser = users.get(role);
		if (authUser == null) {
			throw new IllegalStateException("개발용 회원이 아직 준비되지 않았습니다: " + role);
		}
		return authUser;
	}

}
