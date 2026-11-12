package com.mozip.global.security.graphql;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// application.yml의 graphql.* 설정
@ConfigurationProperties("graphql")
public record GraphqlProperties(
	// introspection(__schema, __type) 허용 여부, 운영에서는 false
	@DefaultValue("true") boolean introspectionEnabled
) {
}
