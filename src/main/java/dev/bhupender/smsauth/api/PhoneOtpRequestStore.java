package dev.bhupender.smsauth.api;

import java.util.HashMap;
import java.util.Map;
import org.keycloak.models.KeycloakSession;

public final class PhoneOtpRequestStore {

    public static final String FIELD_PHONE = "phone";
    public static final String FIELD_SENT_AT = "sentAt";
    public static final String FIELD_RESEND_COUNT = "resendCount";
    public static final String FIELD_OTP_ATTEMPTS = "otpAttempts";
    public static final String FIELD_VERIFICATION_SID = "verificationSid";

    private static final String PREFIX = "sms-auth:phone-otp:";
    private static final long TTL_SECONDS = 15 * 60L;

    private PhoneOtpRequestStore() {
    }

    public static String key(String realmId, String clientId, String phone) {
        return PREFIX + realmId + ":" + clientId + ":" + phone;
    }

    public static Map<String, String> get(KeycloakSession session, String key) {
        Map<String, String> data = session.singleUseObjects().get(key);
        return data == null ? null : new HashMap<>(data);
    }

    public static void put(KeycloakSession session, String key, Map<String, String> data) {
        session.singleUseObjects().put(key, epochSeconds() + TTL_SECONDS, new HashMap<>(data));
    }

    public static void remove(KeycloakSession session, String key) {
        session.singleUseObjects().remove(key);
    }

    private static long epochSeconds() {
        return System.currentTimeMillis() / 1000L;
    }
}
