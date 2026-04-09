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

public class OtpAuthenticator implements Authenticator {

    private static final Logger LOG = Logger.getLogger(OtpAuthenticator.class);
    private static final String TEMPLATE = "otp-form.ftl";

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        context.challenge(buildOtpForm(context, null, null, -1));
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> params = context.getHttpRequest().getDecodedFormParameters();
        String formAction = params.getFirst("action");

        if ("resend".equals(formAction)) {
            handleResend(context);
            return;
        }

        verifyOtp(context, params.getFirst("otp"));
    }

    private void verifyOtp(AuthenticationFlowContext context, String rawOtp) {
        String phone = session(context, SmsAuthConfig.SESSION_PHONE);
        int attempts = intNote(context, SmsAuthConfig.SESSION_OTP_ATTEMPTS);
        int maxAttempts = maxAttempts(context);

        if (rawOtp == null || rawOtp.isBlank()) {
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                    buildOtpForm(context, SmsAuthConfig.MSG_OTP_REQUIRED, null, -1));
            return;
        }

        if (attempts >= maxAttempts) {
            LOG.warnf("Max OTP attempts reached  phone=%s  attempts=%d", phone, attempts);
            context.failureChallenge(AuthenticationFlowError.ACCESS_DENIED,
                    buildOtpForm(context, SmsAuthConfig.MSG_MAX_ATTEMPTS, null, 0));
            return;
        }

        try {
            boolean ok = OtpServiceFactory.getInstance().verifyOtp(phone, rawOtp.trim());
            if (ok) {
                clearNote(context, SmsAuthConfig.SESSION_VERIFICATION_SID);
                LOG.infof("OTP verified  phone=%s", phone);
                context.success();
            } else {
                int newAttempts = attempts + 1;
                setNote(context, SmsAuthConfig.SESSION_OTP_ATTEMPTS, newAttempts);
                int remaining = maxAttempts - newAttempts;
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS,
                        buildOtpForm(context, SmsAuthConfig.MSG_OTP_INVALID, null, remaining));
            }
        } catch (OtpException e) {
            LOG.errorf(e, "OTP verification error  phone=%s", phone);
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR,
                    buildOtpForm(context, SmsAuthConfig.MSG_VERIFY_FAILED, null, -1));
        }
    }

    private void handleResend(AuthenticationFlowContext context) {
        String phone = session(context, SmsAuthConfig.SESSION_PHONE);
        String sentAtStr = session(context, SmsAuthConfig.SESSION_OTP_SENT_AT);
        String verificationSid = session(context, SmsAuthConfig.SESSION_VERIFICATION_SID);
        int resendCount = intNote(context, SmsAuthConfig.SESSION_RESEND_COUNT);
        int maxResends = maxResends(context);
        int cooldownSecs = cooldownSecs(context);

        if (phone == null || phone.isBlank() || sentAtStr == null || sentAtStr.isBlank()) {
            LOG.warn("Ignoring resend request because no OTP session is active");
            context.challenge(buildOtpForm(context, SmsAuthConfig.MSG_RESEND_UNAVAILABLE, null, -1));
            return;
        }

        if (resendCount >= maxResends) {
            context.challenge(buildOtpForm(context, SmsAuthConfig.MSG_RESEND_LIMIT, null, -1));
            return;
        }

        if (sentAtStr != null) {
            long elapsed = (System.currentTimeMillis() - Long.parseLong(sentAtStr)) / 1000L;
            if (elapsed < cooldownSecs) {
                long wait = cooldownSecs - elapsed;
                context.challenge(context.form()
                        .setAttribute("maskedPhone", maskPhone(phone))
                        .setAttribute("otpSentAt", sentAtStr)
                        .setAttribute("cooldownSecs", cooldownSecs)
                        .setAttribute("resendCount", resendCount)
                        .setAttribute("maxResends", maxResends)
                        .setAttribute("canResend", false)
                        .setAttribute("cooldownRemaining", (int) wait)
                        .setError(SmsAuthConfig.MSG_RESEND_COOLDOWN)
                        .createForm(TEMPLATE));
                return;
            }
        }

        try {
            if (verificationSid != null && !verificationSid.isBlank()) {
                OtpServiceFactory.getInstance().cancelOtp(verificationSid);
            }
            String newVerificationSid = OtpServiceFactory.getInstance().sendOtp(phone);
            setNote(context, SmsAuthConfig.SESSION_OTP_SENT_AT, ms());
            setNote(context, SmsAuthConfig.SESSION_RESEND_COUNT, resendCount + 1);
            setNote(context, SmsAuthConfig.SESSION_OTP_ATTEMPTS, 0); // reset attempts
            setNote(context, SmsAuthConfig.SESSION_VERIFICATION_SID, newVerificationSid);
            LOG.infof("OTP resent  phone=%s  resend=%d/%d", phone, resendCount + 1, maxResends);
            context.challenge(buildOtpForm(context, null, SmsAuthConfig.MSG_OTP_RESENT, -1));
        } catch (OtpException e) {
            LOG.errorf(e, "OTP resend failed  phone=%s", phone);
            context.challenge(buildOtpForm(context, SmsAuthConfig.MSG_RESEND_FAILED, null, -1));
        }
    }

    /**
     * @param errorKey          i18n message key for setError(), or null
     * @param infoKey           i18n message key for setInfo(), or null
     * @param remainingAttempts remaining incorrect-OTP attempts (-1 = don't show)
     */
    private Response buildOtpForm(AuthenticationFlowContext context,
            String errorKey, String infoKey, int remainingAttempts) {
        String phone = session(context, SmsAuthConfig.SESSION_PHONE);
        String sentAt = session(context, SmsAuthConfig.SESSION_OTP_SENT_AT);
        int resendCount = intNote(context, SmsAuthConfig.SESSION_RESEND_COUNT);
        int cooldown = cooldownSecs(context);
        int maxResends = maxResends(context);
        boolean otpWasSent = phone != null && !phone.isBlank() && sentAt != null && !sentAt.isBlank();

        var form = context.form()
                .setAttribute("maskedPhone", maskPhone(phone))
                .setAttribute("otpSentAt", otpWasSent ? sentAt : "")
                .setAttribute("cooldownSecs", cooldown)
                .setAttribute("resendCount", resendCount)
                .setAttribute("maxResends", maxResends)
                .setAttribute("canResend", otpWasSent && resendCount < maxResends)
                .setAttribute("remainingAttempts", remainingAttempts);

        if (errorKey != null)
            form = form.setError(errorKey);
        if (infoKey != null)
            form = form.setInfo(infoKey);
        return form.createForm(TEMPLATE);
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7)
            return phone;
        int prefixLen = Math.max(3, phone.startsWith("+") ? 3 : 0);
        String start = phone.substring(0, Math.min(prefixLen + 2, phone.length() - 4));
        String end = phone.substring(phone.length() - 4);
        String mask = "*".repeat(Math.max(0, phone.length() - start.length() - end.length()));
        return start + mask + end;
    }

    private String session(AuthenticationFlowContext ctx, String key) {
        return ctx.getAuthenticationSession().getAuthNote(key);
    }

    private int intNote(AuthenticationFlowContext ctx, String key) {
        String v = session(ctx, key);
        return (v != null) ? Integer.parseInt(v) : 0;
    }

    private void setNote(AuthenticationFlowContext ctx, String key, int value) {
        ctx.getAuthenticationSession().setAuthNote(key, String.valueOf(value));
    }

    private void setNote(AuthenticationFlowContext ctx, String key, String value) {
        ctx.getAuthenticationSession().setAuthNote(key, value);
    }

    private void clearNote(AuthenticationFlowContext ctx, String key) {
        ctx.getAuthenticationSession().removeAuthNote(key);
    }

    private int maxAttempts(AuthenticationFlowContext ctx) {
        return intConfig(ctx, SmsAuthConfig.CONF_MAX_OTP_ATTEMPTS, SmsAuthConfig.DEFAULT_MAX_ATTEMPTS);
    }

    private int maxResends(AuthenticationFlowContext ctx) {
        return intConfig(ctx, SmsAuthConfig.CONF_MAX_RESEND_ATTEMPTS, SmsAuthConfig.DEFAULT_MAX_RESENDS);
    }

    private int cooldownSecs(AuthenticationFlowContext ctx) {
        return intConfig(ctx, SmsAuthConfig.CONF_RESEND_COOLDOWN_SECS, SmsAuthConfig.DEFAULT_RESEND_COOLDOWN);
    }

    private int intConfig(AuthenticationFlowContext ctx, String key, int defaultVal) {
        if (ctx.getAuthenticatorConfig() == null)
            return defaultVal;
        String v = ctx.getAuthenticatorConfig().getConfig().get(key);
        if (v == null)
            return defaultVal;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private static String ms() {
        return String.valueOf(System.currentTimeMillis());
    }

    @Override
    public boolean requiresUser() {
        return true;
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
