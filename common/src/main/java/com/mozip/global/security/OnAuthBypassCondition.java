package com.mozip.global.security;

import java.util.Map;

import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

// AuthBypassProperties와 같은 Binder 변환 규칙으로 읽어 true/on/yes/1, false/off/no/0을 똑같이 해석
class OnAuthBypassCondition implements Condition {

	private static final String PROPERTY = "auth.bypass.enabled";

	@Override
	public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
		Map<String, Object> attributes = metadata.getAnnotationAttributes(ConditionalOnAuthBypass.class.getName());
		boolean expected = attributes == null || (boolean)attributes.get("enabled");
		boolean actual = Binder.get(context.getEnvironment()).bind(PROPERTY, Boolean.class).orElse(false);
		return actual == expected;
	}

}
