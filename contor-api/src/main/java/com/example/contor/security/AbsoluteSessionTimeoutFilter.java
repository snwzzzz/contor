package com.example.contor.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * Ends sessions older than the absolute lifetime, however active they are. Runs before the security context is
 * loaded, so the request continues anonymously (and gets 401 on protected endpoints). Not a bean on purpose:
 * it is only registered inside the security filter chain.
 */
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final Duration absoluteTimeout;
    private final Clock clock;

    public AbsoluteSessionTimeoutFilter(Duration absoluteTimeout, Clock clock) {
        this.absoluteTimeout = absoluteTimeout;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && clock.millis() - session.getCreationTime() > absoluteTimeout.toMillis()) {
            session.invalidate();
        }
        chain.doFilter(request, response);
    }
}
