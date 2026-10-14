package com.example.contor.security;

import com.example.contor.domain.AppUser;
import com.example.contor.domain.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * Principal stored in the session (serialized by Spring Session JDBC). Holds no credentials:
 * {@link #getPassword()} is always {@code null}.
 */
public record AuthenticatedUser(Long userId, String username, UserRole role) implements UserDetails, Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static AuthenticatedUser of(AppUser user) {
        return new AuthenticatedUser(user.getId().id(), user.getUsername().value(), user.getRole());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return null;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
