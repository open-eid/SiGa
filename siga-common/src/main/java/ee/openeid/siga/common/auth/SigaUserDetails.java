package ee.openeid.siga.common.auth;

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
    private String clientName;
    private String serviceName;
    private String serviceUuid;
    private String signingSecret;
    private String skRelyingPartyName;
    private String skRelyingPartyUuid;
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
        return active;
    }

    @Override
    public boolean isAccountNonLocked() {
        return active;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return active;
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    public String getPassword() {
        return signingSecret;
    }

    public String getUsername() {
        return serviceUuid;
    }
}
