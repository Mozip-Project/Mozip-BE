package com.mozip.global.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yml의 aws.s3.* 설정
@ConfigurationProperties("aws.s3")
public record S3Properties(
	String bucket,
	String region,
	@DefaultValue("PT10M") Duration presignedUrlExpiration,
	String cdnDomain
) {
}
