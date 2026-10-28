package com.mozip.global.swagger;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

// API가 응답할 수 있는 에러 코드를 Swagger에 표시 (예: @ApiErrorCodes({"COMMON_400", "COMMON_404"}))
// 어노테이션 값에 ErrorCode[]를 쓸 수 없어 코드 문자열로 지정하며, 없는 코드를 적으면 앱이 시작되지 않음
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiErrorCodes {

	String[] value();

}
