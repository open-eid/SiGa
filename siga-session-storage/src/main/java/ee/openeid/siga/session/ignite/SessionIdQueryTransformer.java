package ee.openeid.siga.session.ignite;

import org.apache.ignite.binary.BinaryObject;
import org.apache.ignite.lang.IgniteClosure;

import javax.cache.Cache;
import java.util.Map;

/**
 * Server-side transformer paired with the {@link SignatureStatusRequestFilter} /
 * {@link CertificateStatusRequestFilter} predicates on the
 * {@link org.apache.ignite.cache.query.ScanQuery} issued by {@link IgniteSessionStatusScanner}.
 * For each cache entry the filter accepts, this closure returns the entry key — the container
 * {@code sessionId} — so the {@link org.apache.ignite.cache.query.QueryCursor} yields plain
 * {@code String} IDs to the client instead of shipping the whole signature/certificate session map
 * over the wire.
 *
 * <p>Reused by both {@code scanSignatureSessions} and {@code scanCertificateSessions} because the
 * cache key is the container {@code sessionId} in either case.
 *
 * <p>NB: This class is loaded into Ignite server nodes via peer class loading.
 * If possible, avoid making changes in this class and in its dependencies!
 */
public class SessionIdQueryTransformer implements IgniteClosure<Cache.Entry<String, Map<String, BinaryObject>>, String> {

    @Override
    public String apply(Cache.Entry<String, Map<String, BinaryObject>> entry) {
        return entry.getKey();
    }
}