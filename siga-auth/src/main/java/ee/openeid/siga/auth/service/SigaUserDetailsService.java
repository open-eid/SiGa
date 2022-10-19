package ee.openeid.siga.auth.service;

import ee.openeid.siga.auth.model.SigaService;
import ee.openeid.siga.auth.repository.ServiceRepository;
import ee.openeid.siga.common.auth.SigaUserDetails;
import ee.openeid.siga.common.model.ServiceType;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@CacheConfig
@RequiredArgsConstructor
public class SigaUserDetailsService implements UserDetailsService {
    private final ServiceRepository serviceRepository;

    @Cacheable(cacheNames = {"AUTH_SERVICES"})
    @Override
    @Transactional
    public SigaUserDetails loadUserByUsername(String serviceUuid) {
        SigaService service = serviceRepository.findByUuid(serviceUuid)
                .orElseThrow(() -> new UsernameNotFoundException("SigaService UUID not found"));
        ServiceType serviceType = determineServiceType(service);
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
                .serviceType(serviceType)
                .active(!service.isInactive())
                .build();
    }

    private ServiceType determineServiceType(SigaService service) {
        if (service.getIpPermissions().isEmpty()) {
            return ServiceType.REST;
        }
        return ServiceType.PROXY;
    }

}
