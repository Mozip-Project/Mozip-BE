package com.mozip.global.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import lombok.RequiredArgsConstructor;

// S3Client는 api(presigned URL 발급)와 worker(생성된 파일 업로드 등)가 공통으로 쓸 수 있어 common에 둠
// worker는 아직 aws.s3.region을 안 설정함. 스프링은 싱글턴 빈을 기동 시 즉시 생성하므로 조건 없이 두면
// worker에서 아무도 안 주입받아도 region이 null인 채로 생성되다 NPE가 남. 실제로 쓰는 모듈에서만 뜨게 함
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aws.s3", name = "region")
public class S3Config {

	private final S3Properties s3Properties;

	@Bean
	public S3Client s3Client() {
		return S3Client.builder()
			.region(Region.of(s3Properties.region()))
			.credentialsProvider(DefaultCredentialsProvider.create())
			.build();
	}

	@Bean
	public S3Presigner s3Presigner() {
		return S3Presigner.builder()
			.region(Region.of(s3Properties.region()))
			.credentialsProvider(DefaultCredentialsProvider.create())
			.build();
	}

}
