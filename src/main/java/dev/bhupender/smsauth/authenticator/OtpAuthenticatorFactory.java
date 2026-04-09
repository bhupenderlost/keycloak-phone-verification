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

public class OtpAuthenticatorFactory implements AuthenticatorFactory {

    public static final String ID = "otp-authenticator";
    private static final OtpAuthenticator SINGLETON = new OtpAuthenticator();

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
        return "Verify SMS Code";
    }

    @Override
    public String getReferenceCategory() {
        return "sms-otp";
    }

    @Override
    public String getHelpText() {
        return "Verifies the SMS one-time code sent to the user's phone (step 2 of 2).";
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
                .name(SmsAuthConfig.CONF_MAX_OTP_ATTEMPTS)
                .label("Max OTP Attempts")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(SmsAuthConfig.DEFAULT_MAX_ATTEMPTS))
                .helpText("Maximum number of wrong codes before the flow is blocked (default: 5).")
                .add()
                .property()
                .name(SmsAuthConfig.CONF_RESEND_COOLDOWN_SECS)
                .label("Resend Cooldown (seconds)")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(SmsAuthConfig.DEFAULT_RESEND_COOLDOWN))
                .helpText("Minimum seconds between resend requests (default: 60).")
                .add()
                .property()
                .name(SmsAuthConfig.CONF_MAX_RESEND_ATTEMPTS)
                .label("Max Resend Attempts")
                .type(ProviderConfigProperty.STRING_TYPE)
                .defaultValue(String.valueOf(SmsAuthConfig.DEFAULT_MAX_RESENDS))
                .helpText("Maximum number of times a user may resend OTP per session (default: 3).")
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
