package dev.bhupender.smsauth.authenticator;

import dev.bhupender.smsauth.config.SmsAuthConfig;
import dev.bhupender.smsauth.service.OtpException;
import dev.bhupender.smsauth.service.OtpServiceFactory;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public class PhoneAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(PhoneAuthenticator.class);
    private static final String TEMPLATE = "phone-form.ftl";
    private static final String E164_REGEX = "^\\+[1-9]\\d{6,14}$";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        context.challenge(buildPhoneForm(context, null, null, null));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> params = context.getHttpRequest().getDecodedFormParameters();

        String rawPhone = blankToNull(params.getFirst("phone"));
        String countryCode = blankToNull(params.getFirst("countryCode"));

        if (rawPhone == null) {
            context.failureChallenge(
                    AuthenticationFlowError.INVALID_CREDENTIALS,
                    buildPhoneForm(context, SmsAuthConfig.MSG_PHONE_REQUIRED, null, countryCode));
            return;
        }

        String phone = normalise(rawPhone, countryCode, defaultCountryCode(context));
        if (!phone.matches(E164_REGEX)) {
            context.failureChallenge(
                    AuthenticationFlowError.INVALID_CREDENTIALS,
                    buildPhoneForm(context, SmsAuthConfig.MSG_PHONE_INVALID, rawPhone, countryCode));
            return;
        }

        UserModel user = resolveUser(context, phone);

        String verificationSid;
        try {
            verificationSid = OtpServiceFactory.getInstance().sendOtp(phone);
        } catch (OtpException e) {
            LOG.errorf(e, "Failed to send OTP  phone=%s", phone);
            context.failureChallenge(
                    AuthenticationFlowError.INTERNAL_ERROR,
                    buildPhoneForm(context, SmsAuthConfig.MSG_SEND_FAILED, rawPhone, countryCode));
            return;
        }

        context.getAuthenticationSession().setAuthNote(SmsAuthConfig.SESSION_PHONE, phone);
        context.getAuthenticationSession().setAuthNote(SmsAuthConfig.SESSION_OTP_SENT_AT, ms());
        context.getAuthenticationSession().setAuthNote(SmsAuthConfig.SESSION_RESEND_COUNT, "0");
        context.getAuthenticationSession().setAuthNote(SmsAuthConfig.SESSION_OTP_ATTEMPTS, "0");
        context.getAuthenticationSession().setAuthNote(SmsAuthConfig.SESSION_VERIFICATION_SID, verificationSid);

        context.setUser(user);
        context.success();
    }

    private Response buildPhoneForm(AuthenticationFlowContext context, String errorKey, String phonePrefill,
            String countryCodePrefill) {
        var form = context.form()
                .setAttribute("defaultCountryCode", defaultCountryCode(context));
        if (errorKey != null)
            form = form.setError(errorKey);
        if (phonePrefill != null)
            form = form.setAttribute("phonePrefill", phonePrefill);
        if (countryCodePrefill != null)
            form = form.setAttribute("countryCodePrefill", countryCodePrefill);
        return form.createForm(TEMPLATE);
    }

    private String normalise(String phone, String countryCode, String defaultCode) {
        String cleaned = phone.replaceAll("[\\s\\-\\(\\)\\.]", "");
        if (cleaned.startsWith("+"))
            return cleaned;
        if (cleaned.startsWith("00"))
            return "+" + cleaned.substring(2);
        String prefix = (countryCode != null && countryCode.startsWith("+")) ? countryCode : defaultCode;
        return prefix + cleaned;
    }

    private String defaultCountryCode(AuthenticationFlowContext context) {
        if (context.getAuthenticatorConfig() == null)
            return SmsAuthConfig.DEFAULT_COUNTRY_CODE;
        return context.getAuthenticatorConfig().getConfig()
                .getOrDefault(SmsAuthConfig.CONF_DEFAULT_COUNTRY_CODE, SmsAuthConfig.DEFAULT_COUNTRY_CODE);
    }

    private UserModel resolveUser(AuthenticationFlowContext context, String phone) {
        var session = context.getSession();
        var realm = context.getRealm();

        UserModel u = session.users().getUserByUsername(realm, phone);
        if (u != null)
            return u;

        u = session.users()
                .searchForUserByUserAttributeStream(realm, SmsAuthConfig.ATTR_PHONE, phone)
                .findFirst().orElse(null);
        if (u != null)
            return u;

        u = session.users()
                .searchForUserByUserAttributeStream(realm, "phone", phone)
                .findFirst().orElse(null);
        if (u != null)
            return u;

        UserModel created = session.users().addUser(realm, phone);
        created.setEnabled(true);
        created.setSingleAttribute(SmsAuthConfig.ATTR_PHONE, phone);
        LOG.infof("Auto-provisioned user  phone=%s  userId=%s", phone, created.getId());
        return created;
    }

    private static String ms() {
        return String.valueOf(System.currentTimeMillis());
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession s, RealmModel r, UserModel u) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession s, RealmModel r, UserModel u) {
    }

    @Override
    public void close() {
    }
}
