package ee.openeid.siga.test.helper;

import org.junit.jupiter.api.extension.ExtendWith;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(EnabledIfSigaProfileActiveCondition.class)
public @interface EnabledIfSigaProfileActive {

    /**
     * Required profiles that must be active in order to enable the target.
     * If any of the required profiles is not active, then the target will be disabled.
     * If the list of required profiles is empty, then the target will be enabled.
     *
     * @return required profiles
     */
    String[] value();

}
