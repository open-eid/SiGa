package ee.openeid.siga.service.signature.test.matcher;

import ee.openeid.siga.common.test.Generators;
import ee.openeid.siga.service.signature.client.SivaDataFile;
import lombok.AllArgsConstructor;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.hamcrest.TypeSafeDiagnosingMatcher;

import java.util.Optional;
import java.util.function.Supplier;

@AllArgsConstructor
public class IsSivaDataFile extends TypeSafeDiagnosingMatcher<SivaDataFile> {

    private final Matcher<String> filenameMatcher;
    private final Matcher<String> hashAlgoMatcher;
    private final Matcher<String> hashMatcher;

    @Override
    protected boolean matchesSafely(SivaDataFile item, Description mismatchDescription) {
        Supplier<String> conjunctionGenerator = Generators.infinite("SivaDataFile ", " and ");
        boolean result = true;

        if (filenameMatcher != null && !filenameMatcher.matches(item.getFilename())) {
            mismatchDescription.appendText(conjunctionGenerator.get()).appendText("filename ");
            filenameMatcher.describeMismatch(item.getFilename(), mismatchDescription);
            result = false;
        }

        if (hashAlgoMatcher != null && !hashAlgoMatcher.matches(item.getHashAlgo())) {
            mismatchDescription.appendText(conjunctionGenerator.get()).appendText("hashAlgo ");
            hashAlgoMatcher.describeMismatch(item.getHashAlgo(), mismatchDescription);
            result = false;
        }

        if (hashMatcher != null && !hashMatcher.matches(item.getHash())) {
            mismatchDescription.appendText(conjunctionGenerator.get()).appendText("hash ");
            hashMatcher.describeMismatch(item.getHash(), mismatchDescription);
            result = false;
        }

        return result;
    }

    @Override
    public void describeTo(Description description) {
        Supplier<String> conjunctionGenerator = Generators.infinite(" with ", " and ");
        description.appendText("SivaDataFile");

        if (filenameMatcher != null) {
            description.appendText(conjunctionGenerator.get()).appendText("filename ").appendDescriptionOf(filenameMatcher);
        }
        if (hashAlgoMatcher != null) {
            description.appendText(conjunctionGenerator.get()).appendText("hashAlgo ").appendDescriptionOf(hashAlgoMatcher);
        }
        if (hashMatcher != null) {
            description.appendText(conjunctionGenerator.get()).appendText("hash ").appendDescriptionOf(hashMatcher);
        }
    }

    public static Matcher<SivaDataFile> isSiVaDataFileWith(String filename, String hashAlgo, String hash) {
        return isSiVaDataFileWith(
                Optional.ofNullable(filename).map(Matchers::equalTo).orElseGet(() -> Matchers.nullValue(String.class)),
                Optional.ofNullable(hashAlgo).map(Matchers::equalTo).orElseGet(() -> Matchers.nullValue(String.class)),
                Optional.ofNullable(hash).map(Matchers::equalTo).orElseGet(() -> Matchers.nullValue(String.class))
        );
    }

    public static Matcher<SivaDataFile> isSiVaDataFileWith(
            Matcher<String> filenameMatcher,
            Matcher<String> hashAlgoMatcher,
            Matcher<String> hashMatcher
    ) {
        return new IsSivaDataFile(filenameMatcher, hashAlgoMatcher, hashMatcher);
    }

}
