package ee.openeid.siga.common.event;

import java.util.Collection;

public class JXPathHelperFunctions {

    private JXPathHelperFunctions() {
        throw new IllegalStateException("Utility class");
    }

    public static int size(Collection list) {
        return list.size();
    }
}
