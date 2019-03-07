package ee.openeid.siga.auth.filter.event;

import ee.openeid.siga.common.event.SigaEventType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SigaEventLog {

    SigaEventType eventType() default SigaEventType.REQUEST;

    boolean logPathVariables() default false;

    ParameterNameXPath[] logRequestBody() default {};

    ParameterNameXPath[] logReturnObject() default {};
}