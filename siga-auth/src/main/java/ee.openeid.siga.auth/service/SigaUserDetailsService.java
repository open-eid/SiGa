package ee.openeid.siga.auth.service;

import ee.openeid.siga.auth.model.SigaService;
import ee.openeid.siga.auth.model.SigaUserDetails;
import ee.openeid.siga.auth.repository.ServiceRepository;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import static lombok.AccessLevel.PRIVATE;

@Service
@FieldDefaults(level = PRIVATE)
public class SigaUserDetailsService implements UserDetailsService {

    @Autowired
    ServiceRepository serviceRepository;

    @Override
    public SigaUserDetails loadUserByUsername(String serviceUuid) throws UsernameNotFoundException {
        SigaService service = serviceRepository.findByUuid(serviceUuid)
                .orElseThrow(() -> new UsernameNotFoundException("SigaService UUID not found"));

        return SigaUserDetails.builder()
                .clientName(service.getClient().getName())
                .serviceName(service.getName())
                .serviceUuid(service.getUuid())
                .signingSecret(service.getSigningSecret())
                .build();
    }
}
