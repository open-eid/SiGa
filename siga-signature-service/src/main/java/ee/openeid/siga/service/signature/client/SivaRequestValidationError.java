package ee.openeid.siga.service.signature.client;

import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class SivaRequestValidationError {
    @Getter
    private final List<SivaErrorResponse> requestErrors = new ArrayList<>();

}
