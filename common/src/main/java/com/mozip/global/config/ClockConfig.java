package com.mozip.global.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// api와 worker가 공통으로 의존하는 common에 둬서, 두 모듈이 같은 Clock을 주입받아 서울 시각을 다룸.
// 테스트에서는 이 빈을 Clock.fixed(...)로 오버라이드해 시각을 고정할 수 있음
@Configuration
public class ClockConfig {

	public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Bean
	public Clock clock() {
		return Clock.system(SEOUL);
	}

}
