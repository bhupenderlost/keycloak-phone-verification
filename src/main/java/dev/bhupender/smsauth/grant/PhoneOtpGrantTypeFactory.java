package dev.bhupender.smsauth.grant;

import org.keycloak.Config;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.protocol.oidc.grants.OAuth2GrantType;
import org.keycloak.protocol.oidc.grants.OAuth2GrantTypeFactory;

public class PhoneOtpGrantTypeFactory implements OAuth2GrantTypeFactory {

    public static final String GRANT_TYPE = "phone_otp";

    @Override
    public OAuth2GrantType create(KeycloakSession session) {
        return new PhoneOtpGrantType();
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return GRANT_TYPE;
    }

    @Override
    public String getShortcut() {
        return GRANT_TYPE;
    }
}
