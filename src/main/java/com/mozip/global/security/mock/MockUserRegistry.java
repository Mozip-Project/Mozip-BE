package com.mozip.global.security.mock;

import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.mozip.domain.user.entity.User;
import com.mozip.domain.user.repository.UserRepository;
import com.mozip.global.security.AuthUser;
import com.mozip.global.security.ConditionalOnAuthBypass;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 인증 우회 중 앱 시작 시 역할별 개발용 회원 생성 (이미 있으면 재사용)
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnAuthBypass
public class MockUserRegistry implements ApplicationRunner {

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;

	private final Map<MockRole, AuthUser> users = new EnumMap<>(MockRole.class);

	@Override
	public void run(ApplicationArguments args) {
		for (MockRole role : MockRole.values()) {
			// 비밀번호를 무작위로 만들어 /auth/login으로는 로그인할 수 없게 함 (우회 중에는 X-Mock-Role로 인증)
			User user = userRepository.findByPhone(role.getPhone())
				.orElseGet(() -> userRepository.save(User.createDevUser(role.getPhone(),
					passwordEncoder.encode(UUID.randomUUID().toString()), role.getUserName(), role.getAuthority(),
					role.getLastRole(), LocalDateTime.now())));
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
