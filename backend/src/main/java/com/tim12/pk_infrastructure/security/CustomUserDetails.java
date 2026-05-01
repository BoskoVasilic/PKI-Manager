package com.tim12.pk_infrastructure.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;
import java.util.Collection;

@Getter
public class CustomUserDetails extends User {

    private final Long userId;
    private final String organization;

    public CustomUserDetails(String email, String password, boolean enabled,
                             Collection<? extends GrantedAuthority> authorities,
                             Long userId, String organization) {
        super(email, password, enabled, true, true, true, authorities);
        this.userId = userId;
        this.organization = organization;
    }
}