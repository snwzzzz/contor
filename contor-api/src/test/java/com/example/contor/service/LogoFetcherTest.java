package com.example.contor.service;

import com.example.contor.domain.SenderDomain;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class LogoFetcherTest {

    @Test
    void isPublicAddress_RejectsLoopbackPrivateLinkLocalAndSharedRanges() throws UnknownHostException {
        assertThat(LogoFetcher.isPublicAddress(ip(127, 0, 0, 1))).isFalse();
        assertThat(LogoFetcher.isPublicAddress(ip(10, 1, 2, 3))).isFalse();
        assertThat(LogoFetcher.isPublicAddress(ip(172, 16, 0, 1))).isFalse();
        assertThat(LogoFetcher.isPublicAddress(ip(192, 168, 1, 1))).isFalse();
        assertThat(LogoFetcher.isPublicAddress(ip(169, 254, 169, 254))).isFalse();
        assertThat(LogoFetcher.isPublicAddress(ip(100, 64, 0, 1))).isFalse();
        assertThat(LogoFetcher.isPublicAddress(ip(0, 0, 0, 0))).isFalse();
        assertThat(LogoFetcher.isPublicAddress(InetAddress.getByName("::1"))).isFalse();
        assertThat(LogoFetcher.isPublicAddress(InetAddress.getByName("fd00::1"))).isFalse();
        assertThat(LogoFetcher.isPublicAddress(InetAddress.getByName("::ffff:192.168.1.1"))).isFalse();
    }

    @Test
    void isPublicAddress_AcceptsPublicAddresses() throws UnknownHostException {
        assertThat(LogoFetcher.isPublicAddress(ip(8, 8, 8, 8))).isTrue();
        assertThat(LogoFetcher.isPublicAddress(InetAddress.getByName("2a00:1450:4001:80b::200e"))).isTrue();
    }

    @Test
    void isAllowedUri_OnlyHttpsOnTheDefaultPort() {
        assertThat(LogoFetcher.isAllowedUri(URI.create("https://siemens.com/favicon.ico"))).isTrue();
        assertThat(LogoFetcher.isAllowedUri(URI.create("https://siemens.com:443/favicon.ico"))).isTrue();
        assertThat(LogoFetcher.isAllowedUri(URI.create("http://siemens.com/favicon.ico"))).isFalse();
        assertThat(LogoFetcher.isAllowedUri(URI.create("https://siemens.com:8443/favicon.ico"))).isFalse();
        assertThat(LogoFetcher.isAllowedUri(URI.create("https://user@siemens.com/favicon.ico"))).isFalse();
        assertThat(LogoFetcher.isAllowedUri(URI.create("file:///etc/passwd"))).isFalse();
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
        assertThat(LogoFetcher.imageTypeOf(new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A})).contains("image/png");
        assertThat(LogoFetcher.imageTypeOf(new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0})).contains("image/jpeg");
        assertThat(LogoFetcher.imageTypeOf(new byte[] {'G', 'I', 'F', '8', '9', 'a'})).contains("image/gif");
        assertThat(LogoFetcher.imageTypeOf(new byte[] {0, 0, 1, 0, 1, 0})).contains("image/x-icon");
        assertThat(LogoFetcher.imageTypeOf("RIFF\0\0\0\0WEBPVP8 ".getBytes())).contains("image/webp");
        assertThat(LogoFetcher.imageTypeOf("<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes())).isEmpty();
        assertThat(LogoFetcher.imageTypeOf("<!doctype html>".getBytes())).isEmpty();
        assertThat(LogoFetcher.imageTypeOf(new byte[0])).isEmpty();
    }

    private static InetAddress ip(int a, int b, int c, int d) throws UnknownHostException {
        return InetAddress.getByAddress(new byte[] {(byte) a, (byte) b, (byte) c, (byte) d});
    }
}
