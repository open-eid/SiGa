package ee.openeid.siga.common.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SigningTypeTest {

    @ParameterizedTest
    @EnumSource(value = SigningType.class, mode = EnumSource.Mode.EXCLUDE, names = "REMOTE")
    void isPollable_WhenTargetIsPollableType_ReturnsTrue(SigningType signingType) {
        assertTrue(signingType.isPollable());
    }

    @ParameterizedTest
    @EnumSource(value = SigningType.class, mode = EnumSource.Mode.INCLUDE, names = "REMOTE")
    void isPollable_WhenTargetIsNonPollableType_ReturnsFalse(SigningType signingType) {
        assertFalse(signingType.isPollable());
    }

    @ParameterizedTest
    @EnumSource(value = SigningType.class, mode = EnumSource.Mode.EXCLUDE, names = "REMOTE")
    void isPollable_WhenArgumentIsPollableType_ReturnsTrue(SigningType signingType) {
        assertTrue(SigningType.isPollable(signingType));
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = SigningType.class, mode = EnumSource.Mode.INCLUDE, names = "REMOTE")
    void isPollable_WhenArgumentIsNonPollableTypeOrNull_ReturnsFalse(SigningType signingType) {
        assertFalse(SigningType.isPollable(signingType));
    }
}
