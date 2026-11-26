package com.mozip.global.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import lombok.RequiredArgsConstructor;

// S3Client는 api(presigned URL 발급)와 worker(생성된 파일 업로드 등)가 공통으로 쓸 수 있어 common에 둠
// worker는 아직 안 씀. @Lazy가 없으면 아무도 안 주입받아도 기동 시 즉시 생성되는데, worker는 aws.s3.region을
// 설정해두지 않아 region이 null이라 그 시점에 NPE가 남. @Lazy로 실제로 주입돼 쓰일 때까지 생성을 미룸
@Configuration
@RequiredArgsConstructor
public class S3Config {

	private final S3Properties s3Properties;

	@Bean
	@Lazy
	public S3Client s3Client() {
		return S3Client.builder()
			.region(Region.of(s3Properties.region()))
			.credentialsProvider(DefaultCredentialsProvider.create())
			.build();
	}

	@Bean
	@Lazy
	public S3Presigner s3Presigner() {
		return S3Presigner.builder()
			.region(Region.of(s3Properties.region()))
			.credentialsProvider(DefaultCredentialsProvider.create())
			.build();
	}

}
