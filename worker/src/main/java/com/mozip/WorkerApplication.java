package com.mozip;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// com.mozip 패키지에 둬야 com.mozip.domain의 리포지토리·엔티티가 컴포넌트/엔티티 스캔에 잡힌다 (결정 006)
@SpringBootApplication
@EnableScheduling
public class WorkerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorkerApplication.class, args);
	}

}
