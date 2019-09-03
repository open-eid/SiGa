package ee.openeid.siga.common.event;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface LogParam {

    SigaEventName.EventParam name();

    String value();

}
