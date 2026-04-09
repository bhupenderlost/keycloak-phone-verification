package dev.bhupender.smsauth.config;

public final class SmsAuthConfig {

    public static final String SESSION_PHONE = "sms_auth_phone";
    public static final String SESSION_OTP_SENT_AT = "sms_auth_otp_sent_at";
    public static final String SESSION_RESEND_COUNT = "sms_auth_resend_count";
    public static final String SESSION_OTP_ATTEMPTS = "sms_auth_otp_attempts";
    public static final String SESSION_VERIFICATION_SID = "sms_auth_verification_sid";

    public static final String ATTR_PHONE = "phoneNumber";

    public static final String CONF_DEFAULT_COUNTRY_CODE = "defaultCountryCode";
    public static final String CONF_RESEND_COOLDOWN_SECS = "resendCooldownSecs";
    public static final String CONF_MAX_RESEND_ATTEMPTS = "maxResendAttempts";
    public static final String CONF_MAX_OTP_ATTEMPTS = "maxOtpAttempts";

    public static final String DEFAULT_COUNTRY_CODE = "+91";
    public static final int DEFAULT_RESEND_COOLDOWN = 60; // seconds
    public static final int DEFAULT_MAX_RESENDS = 3;
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    public static final String MSG_PHONE_REQUIRED = "smsAuthPhoneRequired";
    public static final String MSG_PHONE_INVALID = "smsAuthPhoneInvalid";
    public static final String MSG_SEND_FAILED = "smsAuthSendFailed";
    public static final String MSG_OTP_REQUIRED = "smsAuthOtpRequired";
    public static final String MSG_OTP_INVALID = "smsAuthOtpInvalid";
    public static final String MSG_MAX_ATTEMPTS = "smsAuthMaxAttempts";
    public static final String MSG_VERIFY_FAILED = "smsAuthVerifyFailed";
    public static final String MSG_RESEND_LIMIT = "smsAuthResendLimitReached";
    public static final String MSG_RESEND_COOLDOWN = "smsAuthResendCooldown";
    public static final String MSG_RESEND_UNAVAILABLE = "smsAuthResendUnavailable";
    public static final String MSG_RESEND_FAILED = "smsAuthResendFailed";
    public static final String MSG_OTP_RESENT = "smsAuthOtpResent";

    private SmsAuthConfig() {
    }
}
