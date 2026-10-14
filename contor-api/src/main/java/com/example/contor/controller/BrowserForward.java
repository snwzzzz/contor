package com.example.contor.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.HtmlUtils;

/**
 * Sends the browser on with a meta refresh instead of a 302. After a cross-site arrival (OAuth callback) a redirect
 * keeps the navigation cross-site and SameSite=Strict cookies stay home; a navigation started by this page is same-site.
 */
final class BrowserForward {

    private BrowserForward() {
    }

    static ResponseEntity<String> to(String url) {
        String escaped = HtmlUtils.htmlEscape(url);
        String html = """
                <!doctype html>
                <html><head><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=%s"><title>contor</title></head>
                <body><a href="%s">Continue to contor</a></body></html>
                """.formatted(escaped, escaped);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .cacheControl(CacheControl.noStore())
                .body(html);
    }
}
