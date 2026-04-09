package dev.bhupender.smsauth.service;

public interface OtpService {

    String sendOtp(String phone);

    void cancelOtp(String verificationSid);

    boolean verifyOtp(String phone, String otp);
}
