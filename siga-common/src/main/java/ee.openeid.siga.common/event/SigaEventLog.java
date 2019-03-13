package ee.openeid.siga.common.event;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SigaEventLog {

    SigaEventName eventType() default SigaEventName.REQUEST;

    Param[] logParameters() default {};

    XPath[] logReturnObject() default {};

}