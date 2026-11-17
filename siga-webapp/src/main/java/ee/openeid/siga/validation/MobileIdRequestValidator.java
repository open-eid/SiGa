package ee.openeid.siga.validation;

import ee.openeid.siga.common.exception.RequestValidationException;
import ee.openeid.siga.common.model.MobileIdInformation;
import ee.openeid.siga.common.util.PhoneNumberUtil;
import ee.openeid.siga.service.signature.configuration.MobileIdClientConfigurationProperties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
@Profile("mobileId")
public class MobileIdRequestValidator {

    private static final List<String> MOBILE_ID_LANGUAGES = Arrays.asList("EST", "ENG", "RUS", "LIT");
    private final List<String> allowedCountries;

    MobileIdRequestValidator(MobileIdClientConfigurationProperties properties) {
        this.allowedCountries = properties.allowedCountries();
    }

    public void validate(MobileIdInformation mobileIdInformation) {
        validateLanguage(mobileIdInformation.getLanguage());
        validateMessageToDisplay(mobileIdInformation.getMessageToDisplay());
        validatePhoneNo(mobileIdInformation.getPhoneNo());
        RequestValidator.validatePersonIdentifier(mobileIdInformation.getPersonIdentifier());
    }

    private void validateLanguage(String language) {
        if (!MOBILE_ID_LANGUAGES.contains(language)) {
            throw new RequestValidationException("Invalid Mobile-Id language");
        }
    }

    private void validateMessageToDisplay(String messageToDisplay) {
        if (messageToDisplay != null && messageToDisplay.length() > 100) {
            throw new RequestValidationException("Mobile ID messageToDisplay exceeded maximum allowed length of 100 characters.");
        }
    }

    private void validatePhoneNo(String phoneNo) {
        if (StringUtils.isBlank(phoneNo) || !PhoneNumberUtil.isPhoneNumberValid(phoneNo)) {
            throw new RequestValidationException("Invalid phone No.");
        }
        PhoneNumberUtil.CountryCallingCode countryNumber = PhoneNumberUtil.CountryCallingCode.getCountryByPrefix(phoneNo.substring(0, 4));
        if (countryNumber == null || !allowedCountries.contains(countryNumber.name())) {
            throw new RequestValidationException("Invalid international calling code");
        }
    }
}
