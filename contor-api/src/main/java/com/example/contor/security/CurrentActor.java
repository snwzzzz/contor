package com.example.contor.security;

import com.example.contor.domain.ActorType;
import com.example.contor.domain.UserRole;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/** Who is acting right now: the logged-in user, or the system (startup, background work) when nobody is logged in. */
public final class CurrentActor {

    private CurrentActor() {
    }

    public static Optional<AuthenticatedUser> user() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user
                ? Optional.of(user)
                : Optional.empty();
    }

    public static Optional<Long> userId() {
        return user().map(AuthenticatedUser::userId);
    }

    public static boolean isOwner() {
        return user().map(user -> user.role() == UserRole.OWNER).orElse(false);
    }

    public static ActorType type() {
        return user().isPresent() ? ActorType.USER : ActorType.SYSTEM;
    }
}
