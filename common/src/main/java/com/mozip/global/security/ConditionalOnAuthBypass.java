package com.mozip.global.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Conditional;

// auth.bypass.enabled 값이 enabled와 같을 때만 빈 등록
// @ConditionalOnProperty는 문자열로 비교해 off/0 등을 AuthBypassProperties와 다르게 판단하므로 대신 사용
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Conditional(OnAuthBypassCondition.class)
public @interface ConditionalOnAuthBypass {

	boolean enabled() default true;

}
