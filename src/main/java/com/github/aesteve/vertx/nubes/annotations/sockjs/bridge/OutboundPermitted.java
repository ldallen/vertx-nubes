package com.github.aesteve.vertx.nubes.annotations.sockjs.bridge;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Repeatable(OutboundsPermitted.class)
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface OutboundPermitted {

	String address() default "";

	String addressRegex() default "";

	String requiredAuthority() default "";
}
