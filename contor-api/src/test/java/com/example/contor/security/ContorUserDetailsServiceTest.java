package com.example.contor.security;

import com.example.contor.MutableClock;
import com.example.contor.domain.AppUser;
import com.example.contor.domain.Email;
import com.example.contor.domain.UserRole;
import com.example.contor.domain.Username;
import com.example.contor.persistence.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ContorUserDetailsServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");

    @Mock
    private AppUserRepository appUserRepository;

    private ContorUserDetailsService service;

    @BeforeEach
    void setup() {
        service = new ContorUserDetailsService(appUserRepository, new MutableClock(NOW));
    }

    private AppUser user() {
        return AppUser.builder().id(new AppUser.AppUserId(1L)).username(new Username("owner"))
                .email(new Email("owner@example.com")).passwordHash("hash").role(UserRole.OWNER).build();
    }

    @Test
    void loads_user_with_role_and_hash() {
        when(appUserRepository.findByUsername(new Username("owner"))).thenReturn(Optional.of(user()));

        var details = service.loadUserByUsername("owner");

        assertThat(details.getUsername()).isEqualTo("owner");
        assertThat(details.getPassword()).isEqualTo("hash");
        assertThat(details.getAuthorities()).extracting(Object::toString).containsExactly("ROLE_OWNER");
        assertThat(details.isAccountNonLocked()).isTrue();
    }

    @Test
    void reports_locked_accounts_until_the_lock_expires() {
        var user = user();
        user.setLockedUntil(NOW.plus(Duration.ofMinutes(1)));
        when(appUserRepository.findByUsername(new Username("owner"))).thenReturn(Optional.of(user));
        assertThat(service.loadUserByUsername("owner").isAccountNonLocked()).isFalse();

        user.setLockedUntil(NOW.minusSeconds(1));
        assertThat(service.loadUserByUsername("owner").isAccountNonLocked()).isTrue();
    }

    @Test
    void unknown_and_malformed_usernames_are_not_found() {
        when(appUserRepository.findByUsername(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost")).isInstanceOf(UsernameNotFoundException.class);
        assertThatThrownBy(() -> service.loadUserByUsername("x")).isInstanceOf(UsernameNotFoundException.class);
    }
}
