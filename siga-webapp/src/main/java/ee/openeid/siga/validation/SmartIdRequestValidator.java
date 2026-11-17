package ee.openeid.siga.validation;

import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.common.model.SmartIdInformation;
import ee.openeid.siga.service.signature.configuration.SmartIdClientConfigurationProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
@Profile("smartId")
public class SmartIdRequestValidator {

    private static final Pattern VALID_DOCUMENT_NUMBER = Pattern.compile("^(PNO)[A-Z]{2}-[0-9A-Z*\\-]{1,40}-[0-9A-Z]{4}-(NQ|Q)$");
    private final List<String> allowedCountries;

    SmartIdRequestValidator(SmartIdClientConfigurationProperties properties) {
        this.allowedCountries = properties.allowedCountries();
    }

    public void validateForCertChoice(SmartIdInformation smartIdInformation) {
        validateCountry(smartIdInformation.getCountry());
        RequestValidator.validatePersonIdentifier(smartIdInformation.getPersonIdentifier());
    }

    public void validateForSigning(SmartIdInformation smartIdInformation) {
        validateDisplayText(smartIdInformation.getMessageToDisplay());
        validateDocumentNumber(smartIdInformation.getDocumentNumber());
    }

    private void validateCountry(String country) {
        if (country == null || country.length() != 2) {
            throw new RequestValidationException("Invalid Smart-Id country");
        }
        if (!allowedCountries.contains(country)) {
            throw new RequestValidationException(country + " is not allowed for Smart-Id country");
        }
    }

    private void validateDisplayText(String displayText) {
        if (displayText != null && displayText.length() > 60) {
            throw new RequestValidationException("Invalid Smart-Id message to display");
        }
    }

    private void validateDocumentNumber(String documentNumber) {
        if (StringUtils.isBlank(documentNumber) || !VALID_DOCUMENT_NUMBER.matcher(documentNumber).matches()) {
            throw new RequestValidationException("Invalid Smart-Id documentNumber");
        }
        String country = documentNumber.substring(3, 5);
        if (!allowedCountries.contains(country)) {
            throw new RequestValidationException("Invalid Smart-Id country inside documentNumber");
        }
    }
}
