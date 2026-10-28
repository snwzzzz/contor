package com.example.contor.domain;

import java.util.regex.Pattern;

/**
 * The non-secret part of an external API's authentication ({@link ApiAuthKind} says which settings it needs; the
 * secret itself is an {@link ApiSecret}, stored encrypted on {@link ExternalApi}). All settings are optional here.
 *
 * @param headerName API_KEY_HEADER: the header carrying the key
 * @param queryParam API_KEY_QUERY: the query parameter carrying the key
 * @param tokenUrl   OAUTH2_CLIENT_CREDENTIALS: the token endpoint (HTTPS)
 * @param scope      OAUTH2_CLIENT_CREDENTIALS: the requested scope
 * @param username   BASIC: the user name (the password is the secret)
 * @param clientId   OAUTH2_CLIENT_CREDENTIALS: the client ID (the client secret is the secret)
 */
public record ApiAuthConfig(String headerName, String queryParam, String tokenUrl, String scope, String username, String clientId) {

    public static final ApiAuthConfig NONE = new ApiAuthConfig(null, null, null, null, null, null);

    private static final Pattern QUERY_PARAM = Pattern.compile("^[A-Za-z0-9_.\\-]{1,100}$");
    private static final Pattern PRINTABLE = Pattern.compile("^[\\x20-\\x7E]{1,500}$");
    private static final Pattern TEXT = Pattern.compile("^[^\\x00-\\x1F\\x7F]{1,500}$");

    public ApiAuthConfig {
        if (headerName != null) new ApiHeader(headerName, "");
        if (queryParam != null && !QUERY_PARAM.matcher(queryParam).matches())
            throw new IllegalArgumentException("query parameter is not valid");
        if (tokenUrl != null) new HttpsUrl(tokenUrl);
        if (scope != null && !PRINTABLE.matcher(scope).matches()) throw new IllegalArgumentException("scope is not valid");
        if (username != null && (!TEXT.matcher(username).matches() || username.contains(":")))
            throw new IllegalArgumentException("username is not valid");
        if (clientId != null && !PRINTABLE.matcher(clientId).matches())
            throw new IllegalArgumentException("client ID is not valid");
    }
}
