package dev.bhupender.smsauth.config;

public enum OtpProviderType {

    TWILIO("twilio");

    private final String value;

    OtpProviderType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OtpProviderType fromString(String value) {
        if (value == null)
            return TWILIO;
        for (OtpProviderType type : values()) {
            if (type.value.equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported OTP provider: " + value);
    }
}
