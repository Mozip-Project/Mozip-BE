package com.mozip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

// 알림 SSE 연결의 하트비트(SseEmitterRegistry)가 @Scheduled를 씀
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class MozipApplication {

	public static void main(String[] args) {
		SpringApplication.run(MozipApplication.class, args);
	}

}
