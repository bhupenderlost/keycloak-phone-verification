package dev.bhupender.smsauth.api;

import dev.bhupender.smsauth.support.PhoneVerificationSupport;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;

public final class PhoneApiClientAuthenticator {

    private PhoneApiClientAuthenticator() {
    }

    public static ClientModel authenticate(KeycloakSession session, RealmModel realm,
            MultivaluedMap<String, String> formParams) {
        String clientId = PhoneVerificationSupport.blankToNull(formParams.getFirst("client_id"));
        if (clientId == null) {
            throw new PhoneApiException(OAuthError.unauthorized("invalid_client", "client_id is required"));
        }

        ClientModel client = session.clients().getClientByClientId(realm, clientId);
        if (client == null || !client.isEnabled()) {
            throw new PhoneApiException(OAuthError.unauthorized("invalid_client", "Client authentication failed"));
        }

        if (!"openid-connect".equals(client.getProtocol())) {
            throw new PhoneApiException(OAuthError.forbidden("unauthorized_client", "Client must use OpenID Connect"));
        }

        if (!client.isDirectAccessGrantsEnabled()) {
            throw new PhoneApiException(
                    OAuthError.forbidden("unauthorized_client", "Direct access grants are disabled for this client"));
        }

        if (!client.isPublicClient()) {
            String clientSecret = PhoneVerificationSupport.blankToNull(formParams.getFirst("client_secret"));
            if (clientSecret == null || !client.validateSecret(clientSecret)) {
                throw new PhoneApiException(OAuthError.unauthorized("invalid_client", "Client authentication failed"));
            }
        }

        return client;
    }

    public static final class PhoneApiException extends RuntimeException {
        private final Response response;

        public PhoneApiException(Response response) {
            this.response = response;
        }

        public Response getResponse() {
            return response;
        }
    }
}
