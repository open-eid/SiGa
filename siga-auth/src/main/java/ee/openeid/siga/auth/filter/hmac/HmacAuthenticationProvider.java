package ee.openeid.siga.auth.filter.hmac;

import ee.openeid.siga.auth.service.SigaUserDetailsService;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class HmacAuthenticationProvider extends DaoAuthenticationProvider {

    HmacAuthenticationProvider(SigaUserDetailsService userDetailsService) {
        super.setUserDetailsService(userDetailsService);
    }

    @Override
    protected void additionalAuthenticationChecks(final UserDetails userDetails, final UsernamePasswordAuthenticationToken authentication) {
        final HmacSignature token = (HmacSignature) authentication.getCredentials();
        final byte[] signingSecret = userDetails.getPassword().getBytes();
        try {
            token.validateSignature(signingSecret);
        } catch (HmacAuthenticationException e) {
            throw e;
        } catch (Exception e) {
            throw new InternalAuthenticationServiceException("HMAC Signature validation error", e);
        }
    }
}