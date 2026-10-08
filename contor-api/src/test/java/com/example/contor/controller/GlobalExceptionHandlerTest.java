package com.example.contor.controller;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void client_that_went_away_gets_no_error_body() {
        var gone = new HttpMessageNotWritableException("Could not write JSON",
                new AsyncRequestNotUsableException("ServletOutputStream failed to write", new IOException("aborted")));

        assertThat(handler.handleUnexpected(gone)).isNull();
    }

    @Test
    void other_unexpected_errors_are_500_with_generic_message() {
        var response = handler.handleUnexpected(new IllegalStateException("internal detail"));

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().getMessage()).isEqualTo("An unexpected error occurred");
    }
}
