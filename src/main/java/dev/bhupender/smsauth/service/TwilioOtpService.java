package dev.bhupender.smsauth.service;

import com.twilio.Twilio;
import com.twilio.exception.ApiException;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.rest.verify.v2.service.VerificationUpdater;
import org.jboss.logging.Logger;

public class TwilioOtpService implements OtpService {

    private static final Logger LOG = Logger.getLogger(TwilioOtpService.class);

    private final String serviceSid;

    public TwilioOtpService() {
        String accountSid = requireEnv("TWILIO_ACCOUNT_SID");
        String authToken = requireEnv("TWILIO_AUTH_TOKEN");
        this.serviceSid = requireEnv("TWILIO_SERVICE_SID");

        Twilio.init(accountSid, authToken);
        LOG.info("Twilio OTP service initialised");
    }

    @Override
    public String sendOtp(String phone) {
        try {
            Verification v = Verification.creator(serviceSid, phone, "sms").create();
            LOG.infof("OTP dispatched  phone=%s  sid=%s  status=%s", phone, v.getSid(), v.getStatus());
            return v.getSid();
        } catch (ApiException e) {
            LOG.errorf(e, "Twilio send error  phone=%s  code=%d  msg=%s",
                    phone, e.getCode(), e.getMessage());
            throw new OtpException("Failed to send OTP to " + phone, e);
        }
    }

    @Override
    public void cancelOtp(String verificationSid) {
        try {
            var verification = new VerificationUpdater(
                    serviceSid,
                    verificationSid,
                    Verification.Status.CANCELED)
                    .update();
            LOG.infof("OTP canceled  sid=%s  status=%s", verificationSid, verification.getStatus());
        } catch (ApiException e) {
            if (e.getCode() == 20404 || e.getStatusCode() == 404) {
                LOG.infof("Previous verification already inactive; continuing with new OTP  sid=%s", verificationSid);
                return;
            }
            LOG.errorf(e, "Twilio cancel error  sid=%s  code=%d  msg=%s",
                    verificationSid, e.getCode(), e.getMessage());
            throw new OtpException("Failed to cancel OTP " + verificationSid, e);
        }
    }

    @Override
    public boolean verifyOtp(String phone, String code) {
        try {
            VerificationCheck check = VerificationCheck.creator(serviceSid)
                    .setTo(phone)
                    .setCode(code)
                    .create();
            boolean approved = "approved".equalsIgnoreCase(check.getStatus());
            LOG.infof("OTP check  phone=%s  status=%s", phone, check.getStatus());
            return approved;
        } catch (ApiException e) {
            if (e.getCode() == 20404 || e.getStatusCode() == 404) {
                LOG.infof("OTP expired or not found  phone=%s", phone);
                return false;
            }
            LOG.errorf(e, "Twilio verify error  phone=%s  code=%d  msg=%s",
                    phone, e.getCode(), e.getMessage());
            throw new OtpException("Failed to verify OTP for " + phone, e);
        }
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required environment variable not set: " + name);
        }
        return value;
    }
}
