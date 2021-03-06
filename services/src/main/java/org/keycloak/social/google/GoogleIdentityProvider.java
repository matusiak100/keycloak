/*
 * Copyright 2016 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.keycloak.social.google;

import com.fasterxml.jackson.databind.JsonNode;
import org.jboss.resteasy.spi.ResteasyProviderFactory;
import org.keycloak.OAuth2Constants;
import org.keycloak.OAuthErrorException;
import org.keycloak.broker.oidc.KeycloakOIDCIdentityProvider;
import org.keycloak.broker.oidc.OIDCIdentityProvider;
import org.keycloak.broker.oidc.OIDCIdentityProviderConfig;
import org.keycloak.broker.oidc.mappers.AbstractJsonUserAttributeMapper;
import org.keycloak.broker.provider.BrokeredIdentityContext;
import org.keycloak.broker.provider.IdentityBrokerException;
import org.keycloak.broker.provider.util.SimpleHttp;
import org.keycloak.broker.social.SocialIdentityProvider;
import org.keycloak.common.ClientConnection;
import org.keycloak.common.util.KeycloakUriBuilder;
import org.keycloak.events.Details;
import org.keycloak.events.Errors;
import org.keycloak.events.EventBuilder;
import org.keycloak.models.KeycloakSession;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.IDToken;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.services.ErrorResponseException;

import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.IOException;

/**
 * @author <a href="mailto:sthorger@redhat.com">Stian Thorgersen</a>
 */
public class GoogleIdentityProvider extends OIDCIdentityProvider implements SocialIdentityProvider<OIDCIdentityProviderConfig> {

    public static final String AUTH_URL = "https://accounts.google.com/o/oauth2/auth";
    public static final String TOKEN_URL = "https://www.googleapis.com/oauth2/v3/token";
    public static final String PROFILE_URL = "https://www.googleapis.com/plus/v1/people/me/openIdConnect";
    public static final String DEFAULT_SCOPE = "openid profile email";

    public GoogleIdentityProvider(KeycloakSession session, GoogleIdentityProviderConfig config) {
        super(session, config);
        config.setAuthorizationUrl(AUTH_URL);
        config.setTokenUrl(TOKEN_URL);
        config.setUserInfoUrl(PROFILE_URL);
    }

    @Override
    protected String getDefaultScopes() {
        return DEFAULT_SCOPE;
    }

    @Override
    protected String getUserInfoUrl() {
        String uri = super.getUserInfoUrl();
        if (((GoogleIdentityProviderConfig)getConfig()).isUserIp()) {
            ClientConnection connection = ResteasyProviderFactory.getContextData(ClientConnection.class);
            if (connection != null) {
                uri = KeycloakUriBuilder.fromUri(super.getUserInfoUrl()).queryParam("userIp", connection.getRemoteAddr()).build().toString();
            }

        }
        logger.debugv("GOOGLE userInfoUrl: {0}", uri);
        return uri;
    }

    protected BrokeredIdentityContext extractIdentity(AccessTokenResponse tokenResponse, String accessToken, JsonWebToken idToken) throws IOException {
        String id = idToken.getSubject();
        BrokeredIdentityContext identity = new BrokeredIdentityContext(id);
        String name = (String) idToken.getOtherClaims().get(IDToken.NAME);
        String preferredUsername = (String) idToken.getOtherClaims().get(getUsernameClaimName());
        String email = (String) idToken.getOtherClaims().get(IDToken.EMAIL);

         identity.getContextData().put(VALIDATED_ID_TOKEN, idToken);

        identity.setId(id);
        identity.setName(name);
        identity.setEmail(email);

        identity.setBrokerUserId(getConfig().getAlias() + "." + id);

        if (preferredUsername == null) {
            preferredUsername = email;
        }

        if (preferredUsername == null) {
            preferredUsername = id;
        }

        identity.setUsername(preferredUsername);
        if (tokenResponse != null && tokenResponse.getSessionState() != null) {
            identity.setBrokerSessionId(getConfig().getAlias() + "." + tokenResponse.getSessionState());
        }
        if (tokenResponse != null) identity.getContextData().put(FEDERATED_ACCESS_TOKEN_RESPONSE, tokenResponse);
        if (tokenResponse != null) processAccessTokenResponse(identity, tokenResponse);
        return identity;
    }


    @Override
    public BrokeredIdentityContext exchangeExternal(EventBuilder event, MultivaluedMap<String, String> params) {
        return null;
    }


}
