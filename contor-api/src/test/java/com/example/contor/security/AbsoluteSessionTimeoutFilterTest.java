package com.example.contor.security;

import com.example.contor.MutableClock;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class AbsoluteSessionTimeoutFilterTest {

    private final MutableClock clock = new MutableClock(Instant.now());
    private final AbsoluteSessionTimeoutFilter filter = new AbsoluteSessionTimeoutFilter(Duration.ofHours(12), clock);

    @Test
    void keeps_sessions_younger_than_the_absolute_lifetime() throws Exception {
        var session = new MockHttpSession();
        var request = new MockHttpServletRequest();
        request.setSession(session);
        clock.advance(Duration.ofHours(11));
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(session.isInvalid()).isFalse();
        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void invalidates_sessions_older_than_the_absolute_lifetime_and_continues_anonymously() throws Exception {
        var session = new MockHttpSession();
        var request = new MockHttpServletRequest();
        request.setSession(session);
        clock.advance(Duration.ofHours(12).plusSeconds(1));
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(session.isInvalid()).isTrue();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void ignores_requests_without_session() throws Exception {
        var request = new MockHttpServletRequest();
        var chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(request.getSession(false)).isNull();
        assertThat(chain.getRequest()).isSameAs(request);
    }
}
