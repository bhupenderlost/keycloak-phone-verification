package dev.bhupender.smsauth.grant;

import dev.bhupender.smsauth.api.OAuthError;
import dev.bhupender.smsauth.api.PhoneApiClientAuthenticator;
import dev.bhupender.smsauth.api.PhoneOtpRequestStore;
import dev.bhupender.smsauth.config.SmsAuthConfig;
import dev.bhupender.smsauth.service.OtpException;
import dev.bhupender.smsauth.service.OtpServiceFactory;
import dev.bhupender.smsauth.support.PhoneVerificationSupport;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.events.EventType;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.protocol.oidc.grants.OAuth2GrantType;

public class PhoneOtpGrantType implements OAuth2GrantType {

    private static final Logger LOG = Logger.getLogger(PhoneOtpGrantType.class);

    @Override
    public EventType getEventType() {
        return EventType.LOGIN;
    }

    @Override
    public Response process(Context context) {
        KeycloakSession session = context.getSession();
        RealmModel realm = context.getRealm();
        MultivaluedMap<String, String> formParams = context.getFormParams();

        final ClientModel client;
        try {
            client = PhoneApiClientAuthenticator.authenticate(session, realm, formParams);
        } catch (PhoneApiClientAuthenticator.PhoneApiException e) {
            return e.getResponse();
        }

        String rawPhone = PhoneVerificationSupport.blankToNull(formParams.getFirst("phone_number"));
        String otp = PhoneVerificationSupport.blankToNull(formParams.getFirst("otp"));
        if (rawPhone == null) {
            return OAuthError.badRequest("invalid_request", "phone_number is required");
        }
        if (otp == null) {
            return OAuthError.badRequest("invalid_request", "otp is required");
        }

        String countryCode = PhoneVerificationSupport.blankToNull(formParams.getFirst("country_code"));
        String phone = PhoneVerificationSupport.normalisePhone(rawPhone, countryCode,
                SmsAuthConfig.DEFAULT_COUNTRY_CODE);
        if (!phone.matches(PhoneVerificationSupport.E164_REGEX)) {
            return OAuthError.badRequest("invalid_request", "phone_number must be a valid E.164 phone number");
        }

        String key = PhoneOtpRequestStore.key(realm.getId(), client.getId(), phone);
        Map<String, String> state = PhoneOtpRequestStore.get(session, key);
        if (state == null) {
            return OAuthError.badRequest("invalid_grant", "No active OTP request found for this phone number");
        }

        int attempts = intValue(state.get(PhoneOtpRequestStore.FIELD_OTP_ATTEMPTS));
        if (attempts >= SmsAuthConfig.DEFAULT_MAX_ATTEMPTS) {
            PhoneOtpRequestStore.remove(session, key);
            return OAuthError.badRequest("invalid_grant", "Maximum OTP verification attempts reached");
        }

        try {
            if (!OtpServiceFactory.getInstance().verifyOtp(phone, otp)) {
                state.put(PhoneOtpRequestStore.FIELD_OTP_ATTEMPTS, String.valueOf(attempts + 1));
                PhoneOtpRequestStore.put(session, key, state);
                return OAuthError.badRequest("invalid_grant", "Invalid OTP");
            }

            PhoneOtpRequestStore.remove(session, key);
            UserModel user = PhoneVerificationSupport.resolveUser(session, realm, phone);

            context.getEvent()
                    .detail("auth_method", "phone_otp")
                    .detail("phone_number", phone)
                    .user(user);

            String scope = PhoneVerificationSupport.blankToNull(formParams.getFirst("scope"));
            return PhoneTokenIssuer.issue(session, realm, client, user, scope, context.getEvent());
        } catch (OtpException e) {
            LOG.errorf(e, "Phone OTP grant verification failed  clientId=%s  phone=%s", client.getClientId(), phone);
            return OAuthError.serverError("Failed to verify OTP");
        } catch (IllegalStateException e) {
            LOG.errorf(e, "Phone OTP grant token issuance failed  clientId=%s  phone=%s", client.getClientId(), phone);
            return OAuthError.serverError("Failed to issue tokens");
        }
    }

    private static int intValue(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void close() {
    }
}
