package ee.openeid.siga.auth.service;

import ee.openeid.siga.auth.model.SigaService;
import ee.openeid.siga.auth.repository.ServiceRepository;
import ee.openeid.siga.common.auth.SigaUserDetails;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@CacheConfig
public class SigaUserDetailsService implements UserDetailsService {

    @Autowired
    private ServiceRepository serviceRepository;

    @Cacheable(cacheNames = {"SIGA-AUTH-SERVICES"})
    @Override
    public SigaUserDetails loadUserByUsername(String serviceUuid) {
        SigaService service = serviceRepository.findByUuid(serviceUuid)
                .orElseThrow(() -> new UsernameNotFoundException("SigaService UUID not found"));
        return SigaUserDetails.builder()
                .clientName(service.getClient().getName())
                .clientUuid(service.getClient().getUuid())
                .serviceName(service.getName())
                .serviceUuid(service.getUuid())
                .signingSecret(service.getSigningSecret())
                .skRelyingPartyName(service.getSkRelyingPartyName())
                .skRelyingPartyUuid(service.getSkRelyingPartyUuid())
                .smartIdRelyingPartyName(service.getSmartIdRelyingPartyName())
                .smartIdRelyingPartyUuid(service.getSmartIdRelyingPartyUuid())
                .build();
    }
}
