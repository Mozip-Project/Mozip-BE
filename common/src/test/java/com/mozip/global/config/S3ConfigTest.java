package com.mozip.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class S3ConfigTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesConfig.class, S3Config.class);

	@Test
	@DisplayName("aws.s3.region이 없으면 S3Client/S3Presigner 빈을 만들지 않고 기동도 실패하지 않는다")
	void regionMissing() {
		runner.run(context -> {
			assertThat(context).hasNotFailed();
			assertThat(context).doesNotHaveBean("s3Client");
			assertThat(context).doesNotHaveBean("s3Presigner");
		});
	}

	@Test
	@DisplayName("aws.s3.region이 있으면 S3Client/S3Presigner 빈을 만든다")
	void regionConfigured() {
		runner.withPropertyValues("aws.s3.region=ap-northeast-2")
			.run(context -> {
				assertThat(context).hasSingleBean(com.mozip.global.config.S3Config.class);
				assertThat(context).hasBean("s3Client");
				assertThat(context).hasBean("s3Presigner");
			});
	}

	@EnableConfigurationProperties(S3Properties.class)
	static class PropertiesConfig {

	}

}
