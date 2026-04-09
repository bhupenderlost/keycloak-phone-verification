package dev.bhupender.smsauth.service;

import dev.bhupender.smsauth.config.OtpProviderType;
import org.jboss.logging.Logger;

public final class OtpServiceFactory {

    private static final Logger LOG = Logger.getLogger(OtpServiceFactory.class);

    private static volatile OtpService instance;

    private OtpServiceFactory() {
    }

    public static OtpService getInstance() {
        if (instance == null) {
            synchronized (OtpServiceFactory.class) {
                if (instance == null) {
                    instance = create();
                }
            }
        }
        return instance;
    }

    private static OtpService create() {
        String providerEnv = System.getenv("OTP_PROVIDER");
        OtpProviderType type = OtpProviderType.fromString(
                (providerEnv == null || providerEnv.isBlank()) ? OtpProviderType.TWILIO.getValue() : providerEnv);
        LOG.infof("Creating OTP service  provider=%s", type.getValue());
        return switch (type) {
            case TWILIO -> new TwilioOtpService();
        };
    }
}
