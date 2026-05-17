package ee.openeid.siga.session.ignite;

/**
 * Names of the three Ignite caches used by the SiGa session storage backend. Each cache holds one
 * concern: the container session itself, and two per-container maps of {@code SignatureSession} and
 * {@code CertificateSession} entries.
 *
 * <p>Specific to the Ignite backend — Redis uses hash-tagged key families and does not need
 * cache-name identifiers.
 */
public enum CacheName {
    CONTAINER_SESSION,
    SIGNATURE_SESSION,
    CERTIFICATE_SESSION
}

