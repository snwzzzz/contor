package com.example.contor.service;

import com.example.contor.domain.SenderDomain;
import com.example.contor.security.OutboundRequestException;
import com.example.contor.security.OutboundRequestGuard;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogoFetcherTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A};

    private final OutboundRequestGuard guard = mock(OutboundRequestGuard.class);
    private final LogoFetcher fetcher = new LogoFetcher(guard);

    @Test
    void fetch_ReturnsTheFirstRasterImage() {
        when(guard.get(eq(URI.create("https://siemens.com/apple-touch-icon.png")), any(), any(), anyInt()))
                .thenReturn(new OutboundRequestGuard.Response(404, Map.of("content-type", "text/html"), new byte[0], false));
        when(guard.get(eq(URI.create("https://siemens.com/favicon.ico")), any(), any(), anyInt()))
                .thenReturn(new OutboundRequestGuard.Response(200, Map.of("content-type", "image/png"), PNG, false));

        assertThat(fetcher.fetch(new SenderDomain("siemens.com")))
                .hasValueSatisfying(image -> assertThat(image.contentType()).isEqualTo("image/png"));
    }

    @Test
    void fetch_SkipsRefusedRequestsTruncatedBodiesAndNonImages() {
        when(guard.get(eq(URI.create("https://news.siemens.com/apple-touch-icon.png")), any(), any(), anyInt()))
                .thenThrow(new OutboundRequestException("news.siemens.com is not a public address"));
        when(guard.get(eq(URI.create("https://news.siemens.com/favicon.ico")), any(), any(), anyInt()))
                .thenReturn(new OutboundRequestGuard.Response(200, Map.of("content-type", "image/png"), PNG, true));
        when(guard.get(eq(URI.create("https://siemens.com/apple-touch-icon.png")), any(), any(), anyInt()))
                .thenReturn(new OutboundRequestGuard.Response(200, Map.of("content-type", "image/svg+xml"), "<svg/>".getBytes(), false));
        when(guard.get(eq(URI.create("https://siemens.com/favicon.ico")), any(), any(), anyInt()))
                .thenReturn(new OutboundRequestGuard.Response(200, Map.of("content-type", "text/html"), "<!doctype html>".getBytes(), false));

        assertThat(fetcher.fetch(new SenderDomain("news.siemens.com"))).isEmpty();
    }

    @Test
    void fetch_AsksForImagesOnly() {
        when(guard.get(any(), eq(Map.of("Accept", "image/png,image/x-icon,image/*;q=0.8", "User-Agent", "contor/1.0 (sender logo)")),
                any(), eq(256 * 1024))).thenReturn(new OutboundRequestGuard.Response(200, Map.of("content-type", "image/png"), PNG, false));

        assertThat(fetcher.fetch(new SenderDomain("siemens.com"))).isPresent();
    }

    @Test
    void candidateUris_TryTheDomainThenItsParent_TouchIconBeforeFavicon() {
        assertThat(LogoFetcher.candidateUris(new SenderDomain("news.microsoft.com"))).containsExactly(
                URI.create("https://news.microsoft.com/apple-touch-icon.png"),
                URI.create("https://news.microsoft.com/favicon.ico"),
                URI.create("https://microsoft.com/apple-touch-icon.png"),
                URI.create("https://microsoft.com/favicon.ico"));
    }

    @Test
    void imageTypeOf_AcceptsRasterImagesOnly() {
        assertThat(LogoFetcher.imageTypeOf(PNG)).contains("image/png");
        assertThat(LogoFetcher.imageTypeOf(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).contains("image/jpeg");
        assertThat(LogoFetcher.imageTypeOf(new byte[] {'G', 'I', 'F', '8', '9', 'a'})).contains("image/gif");
        assertThat(LogoFetcher.imageTypeOf(new byte[] {0, 0, 1, 0, 1, 0})).contains("image/x-icon");
        assertThat(LogoFetcher.imageTypeOf("RIFF\0\0\0\0WEBPVP8 ".getBytes())).contains("image/webp");
        assertThat(LogoFetcher.imageTypeOf("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes())).isEmpty();
        assertThat(LogoFetcher.imageTypeOf("<!doctype html>".getBytes())).isEmpty();
        assertThat(LogoFetcher.imageTypeOf(new byte[0])).isEmpty();
    }
}
