package ee.openeid.siga.auth.filter.event;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface JXPath {
    String logName();

    String xpath();
}
