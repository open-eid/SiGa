package ee.openeid.siga.service.signature.test.matcher;

import ee.openeid.siga.common.test.Generators;
import ee.openeid.siga.service.signature.client.SignatureFile;
import ee.openeid.siga.service.signature.client.SivaDataFile;
import lombok.AllArgsConstructor;
import org.apache.commons.codec.binary.Base64;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Optional;
import java.util.function.Supplier;

@AllArgsConstructor
public class IsSignatureFile extends TypeSafeDiagnosingMatcher<SignatureFile> {

    private final Matcher<String> signatureMatcher;
    private final Matcher<? extends Iterable<? extends SivaDataFile>> dataFilesMatcher;

    @Override
    protected boolean matchesSafely(SignatureFile item, Description mismatchDescription) {
        Supplier<String> conjunctionGenerator = Generators.infinite("SignatureFile ", " and ");
        boolean result = true;

        if (signatureMatcher != null && !signatureMatcher.matches(item.getSignature())) {
            mismatchDescription.appendText(conjunctionGenerator.get()).appendText("signature ");
            signatureMatcher.describeMismatch(item.getSignature(), mismatchDescription);
            result = false;
        }

        if (dataFilesMatcher != null && !dataFilesMatcher.matches(item.getDatafiles())) {
            mismatchDescription.appendText(conjunctionGenerator.get()).appendText("dataFiles ");
            dataFilesMatcher.describeMismatch(item.getDatafiles(), mismatchDescription);
            result = false;
        }

        return result;
    }

    @Override
    public void describeTo(Description description) {
        Supplier<String> conjunctionGenerator = Generators.infinite(" with ", " and ");
        description.appendText("SignatureFile");

        if (signatureMatcher != null) {
            description.appendText(conjunctionGenerator.get()).appendText("signature ").appendDescriptionOf(signatureMatcher);
        }
        if (dataFilesMatcher != null) {
            description.appendText(conjunctionGenerator.get()).appendText("dataFiles ").appendDescriptionOf(dataFilesMatcher);
        }
    }

    public static Matcher<SignatureFile> isSignatureFileWith(
            String signature,
            Matcher<? extends Iterable<? extends SivaDataFile>> dataFilesMatcher
    ) {
        return isSignatureFileWith(
                Optional.ofNullable(signature).map(Matchers::equalTo).orElseGet(() -> Matchers.nullValue(String.class)),
                dataFilesMatcher
        );
    }

    public static Matcher<SignatureFile> isSignatureFileWith(
            byte[] signatureBytes,
            Matcher<? extends Iterable<? extends SivaDataFile>> dataFilesMatcher
    ) {
        return isSignatureFileWith(
                Optional.ofNullable(signatureBytes)
                        .map(bytes -> Matchers.equalTo(Base64.encodeBase64String(bytes)))
                        .orElseGet(() -> Matchers.nullValue(String.class)),
                dataFilesMatcher
        );
    }

    public static Matcher<SignatureFile> isSignatureFileWith(
            Matcher<String> signatureMatcher,
            Matcher<? extends Iterable<? extends SivaDataFile>> dataFilesMatcher
    ) {
        return new IsSignatureFile(signatureMatcher, dataFilesMatcher);
    }

}
