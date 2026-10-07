package com.example.contor;

import com.example.contor.domain.UserRole;
import com.example.contor.security.AuthenticatedUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;

/** Authenticated principals for MockMvc requests. */
public final class TestAuth {

    private TestAuth() {
    }

    public static RequestPostProcessor owner() {
        return as(new AuthenticatedUser(1L, "owner", UserRole.OWNER));
    }

    public static RequestPostProcessor user() {
        return as(new AuthenticatedUser(2L, "user", UserRole.USER));
    }

    private static RequestPostProcessor as(AuthenticatedUser principal) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
    }
}
