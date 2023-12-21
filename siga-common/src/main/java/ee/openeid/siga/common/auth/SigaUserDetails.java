package ee.openeid.siga.common.auth;

import ee.openeid.siga.common.model.ServiceType;
import lombok.Builder;
import lombok.Data;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Data
@Builder
public class SigaUserDetails implements UserDetails {
    private int serviceId;
    private String clientName;
    private String clientUuid;
    private String serviceName;
    private String serviceUuid;
    private String signingSecret;
    private String skRelyingPartyName;
    private String skRelyingPartyUuid;
    private ServiceType serviceType;
    private String smartIdRelyingPartyName;
    private String smartIdRelyingPartyUuid;
    @Builder.Default()
    private boolean active = true;
    @Builder.Default()
    private List<String> roles = new ArrayList<>();

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AuthorityUtils.createAuthorityList(roles.toArray(new String[roles.size()]));
    }

    @Override
    public boolean isAccountNonExpired() {
        return isActive();
    }

    @Override
    public boolean isAccountNonLocked() {
        return isActive();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return isActive();
    }

    @Override
    public boolean isEnabled() {
        return isActive();
    }

    private boolean isActive() {
        return active;
    }

    public String getPassword() {
        return signingSecret;
    }

    public String getUsername() {
        return serviceUuid;
    }
}
