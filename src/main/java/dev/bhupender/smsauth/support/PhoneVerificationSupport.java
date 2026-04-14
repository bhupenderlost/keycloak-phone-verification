package dev.bhupender.smsauth.support;

import dev.bhupender.smsauth.config.SmsAuthConfig;
import org.jboss.logging.Logger;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

public final class PhoneVerificationSupport {

    private static final Logger LOG = Logger.getLogger(PhoneVerificationSupport.class);
    public static final String E164_REGEX = "^\\+[1-9]\\d{6,14}$";

    private PhoneVerificationSupport() {
    }

    public static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }

    public static String normalisePhone(String phone, String countryCode, String defaultCountryCode) {
        String cleaned = phone.replaceAll("[\\s\\-\\(\\)\\.]", "");
        if (cleaned.startsWith("+")) {
            return cleaned;
        }
        if (cleaned.startsWith("00")) {
            return "+" + cleaned.substring(2);
        }
        String prefix = (countryCode != null && countryCode.startsWith("+")) ? countryCode : defaultCountryCode;
        return prefix + cleaned;
    }

    public static UserModel resolveUser(KeycloakSession session, RealmModel realm, String phone) {
        UserModel user = session.users().getUserByUsername(realm, phone);
        if (user != null) {
            return user;
        }

        user = session.users()
                .searchForUserByUserAttributeStream(realm, SmsAuthConfig.ATTR_PHONE, phone)
                .findFirst()
                .orElse(null);
        if (user != null) {
            return user;
        }

        user = session.users()
                .searchForUserByUserAttributeStream(realm, "phone", phone)
                .findFirst()
                .orElse(null);
        if (user != null) {
            return user;
        }

        UserModel created = session.users().addUser(realm, phone);
        created.setEnabled(true);
        created.setSingleAttribute(SmsAuthConfig.ATTR_PHONE, phone);
        LOG.infof("Auto-provisioned user  phone=%s  userId=%s", phone, created.getId());
        return created;
    }
}
