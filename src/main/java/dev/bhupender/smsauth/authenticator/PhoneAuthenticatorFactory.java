package dev.bhupender.smsauth.authenticator;

import dev.bhupender.smsauth.config.SmsAuthConfig;
import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.provider.ProviderConfigurationBuilder;

import java.util.List;

public class PhoneAuthenticatorFactory implements AuthenticatorFactory {

    public static final String ID = "phone-authenticator";
    private static final PhoneAuthenticator SINGLETON = new PhoneAuthenticator();

    private static final AuthenticationExecutionModel.Requirement[] REQUIREMENTS = {
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.ALTERNATIVE,
            AuthenticationExecutionModel.Requirement.DISABLED
    };

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public String getDisplayType() {
        return "Collect Phone Number";
    }

    @Override
    public String getReferenceCategory() {
        return "sms-otp";
    }

    @Override
    public String getHelpText() {
        return "Collects the user's phone number and sends a one-time code via SMS (step 1 of 2).";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return REQUIREMENTS;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return ProviderConfigurationBuilder.create()
                .property()
                .name(SmsAuthConfig.CONF_DEFAULT_COUNTRY_CODE)
                .label("Default Country Code")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(SmsAuthConfig.DEFAULT_COUNTRY_CODE)
                .helpText("E.164 country-code prefix applied when the user omits it "
                        + "(e.g. +91 for India, +1 for US/Canada).")
                .add()
                .build();
    }

    @Override
    public Authenticator create(KeycloakSession session) {
        return SINGLETON;
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
}
