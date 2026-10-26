package com.example.contor.mailprovider;

import com.example.contor.domain.MailProvider;

/**
 * OAuth 2.0 authorization-code flow (with PKCE) of one mail provider: the sign-in URL, the code exchange and the token
 * refresh. The state parameter and the PKCE verifier come from {@link OAuthStateCodec}; clients never see the user.
 * <p>
 * Plug-in point: to support a new mail provider, add the {@link MailProvider} constant, one {@code @Component}
 * implementing this interface and one implementing {@link MailboxClient}. {@link MailProviderRegistry} picks both up;
 * the services stay unchanged.
 */
public interface MailOAuthClient {

    MailProvider provider();

    /** Short product name shown on the connect button ("Outlook", "Gmail"). */
    String displayName();

    /** Client id and secret are set; an unconfigured provider is not offered for connecting. */
    boolean isConfigured();

    /** Sign-in URL the browser is sent to; {@code loginHint} (may be null) pre-fills the address. */
    String authorizeUrl(String state, String codeChallenge, String loginHint);

    OAuthTokens exchangeCode(String code, String codeVerifier);

    /** New access token; the answer's refresh token is {@code null} when the provider keeps the old one valid. */
    OAuthTokens refresh(String refreshToken);
}
