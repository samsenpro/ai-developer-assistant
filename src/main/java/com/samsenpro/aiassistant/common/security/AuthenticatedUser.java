package com.samsenpro.aiassistant.common.security;

import com.samsenpro.aiassistant.auth.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Usuario autenticado de la petición. Los controllers lo reciben con
 * {@code @AuthenticationPrincipal} y pasan su {@code id} a los servicios, que comprueban la
 * propiedad de cada recurso.
 */
public record AuthenticatedUser(Long id, String username, String passwordHash) implements UserDetails {

    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    public static AuthenticatedUser from(User user) {
        return new AuthenticatedUser(user.getId(), user.getUsername(), user.getPasswordHash());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String toString() {
        return "AuthenticatedUser[id=" + id + ", username=" + username + "]";
    }
}
