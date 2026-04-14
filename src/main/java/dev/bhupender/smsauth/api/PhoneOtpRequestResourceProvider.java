package dev.bhupender.smsauth.api;

import dev.bhupender.smsauth.config.SmsAuthConfig;
import dev.bhupender.smsauth.service.OtpException;
import dev.bhupender.smsauth.service.OtpServiceFactory;
import dev.bhupender.smsauth.support.PhoneVerificationSupport;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.services.resource.RealmResourceProvider;

public class PhoneOtpRequestResourceProvider implements RealmResourceProvider {

    private static final Logger LOG = Logger.getLogger(PhoneOtpRequestResourceProvider.class);

    private final KeycloakSession session;

    public PhoneOtpRequestResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return this;
    }

    @POST
    @Path("request-otp")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    public Response requestOtp(MultivaluedMap<String, String> formParams) {
        RealmModel realm = session.getContext().getRealm();
        if (realm == null) {
            return OAuthError.serverError("Realm context is unavailable");
        }

        final ClientModel client;
        try {
            client = PhoneApiClientAuthenticator.authenticate(session, realm, formParams);
        } catch (PhoneApiClientAuthenticator.PhoneApiException e) {
            return e.getResponse();
        }

        String rawPhone = PhoneVerificationSupport.blankToNull(formParams.getFirst("phone_number"));
        if (rawPhone == null) {
            return OAuthError.badRequest("invalid_request", "phone_number is required");
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
            state = new LinkedHashMap<>();
        }

        long now = System.currentTimeMillis();
        long sentAt = longValue(state.get(PhoneOtpRequestStore.FIELD_SENT_AT));
        int resendCount = intValue(state.get(PhoneOtpRequestStore.FIELD_RESEND_COUNT));

        if (sentAt > 0) {
            long elapsedSecs = (now - sentAt) / 1000L;
            if (elapsedSecs < SmsAuthConfig.DEFAULT_RESEND_COOLDOWN) {
                return OAuthError.tooManyRequests(
                        "slow_down",
                        "Please wait " + (SmsAuthConfig.DEFAULT_RESEND_COOLDOWN - elapsedSecs)
                                + " seconds before requesting another OTP");
            }
            if (resendCount >= SmsAuthConfig.DEFAULT_MAX_RESENDS) {
                return OAuthError.tooManyRequests("slow_down", "Maximum OTP resend attempts reached");
            }
        }

        String previousVerificationSid = PhoneVerificationSupport.blankToNull(
                state.get(PhoneOtpRequestStore.FIELD_VERIFICATION_SID));

        try {
            if (previousVerificationSid != null) {
                OtpServiceFactory.getInstance().cancelOtp(previousVerificationSid);
            }
            String verificationSid = OtpServiceFactory.getInstance().sendOtp(phone);

            state.put(PhoneOtpRequestStore.FIELD_PHONE, phone);
            state.put(PhoneOtpRequestStore.FIELD_SENT_AT, String.valueOf(now));
            state.put(PhoneOtpRequestStore.FIELD_RESEND_COUNT, String.valueOf(sentAt > 0 ? resendCount + 1 : 0));
            state.put(PhoneOtpRequestStore.FIELD_OTP_ATTEMPTS, "0");
            state.put(PhoneOtpRequestStore.FIELD_VERIFICATION_SID, verificationSid);
            PhoneOtpRequestStore.put(session, key, state);

            Map<String, Object> entity = new LinkedHashMap<>();
            entity.put("status", "otp_sent");
            entity.put("phone_number", phone);
            entity.put("expires_in", 600);
            entity.put("resend_available_in", SmsAuthConfig.DEFAULT_RESEND_COOLDOWN);

            return Response.ok(entity)
                    .type(MediaType.APPLICATION_JSON_TYPE)
                    .header("Cache-Control", "no-store")
                    .header("Pragma", "no-cache")
                    .build();
        } catch (OtpException e) {
            LOG.errorf(e, "Failed to send API OTP  clientId=%s  phone=%s", client.getClientId(), phone);
            return OAuthError.serverError("Failed to send OTP");
        }
    }

    private static int intValue(String value) {
        try {
            return value == null ? 0 : Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long longValue(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    @Override
    public void close() {
    }
}
