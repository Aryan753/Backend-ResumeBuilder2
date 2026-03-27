package com.resumebuilder.service;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {

    private final JavaMailSender mailSender;

    // In-memory store: email → {otp, expiryMs}
    private final Map<String, long[]> otpStore = new ConcurrentHashMap<>();
    private static final long OTP_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes

    public OtpService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendOtp(String email) {
        String otp = String.valueOf(100000 + new Random().nextInt(900000));
        long expiry = System.currentTimeMillis() + OTP_EXPIRY_MS;
        otpStore.put(email.toLowerCase(), new long[]{Long.parseLong(otp), expiry});

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("Your Resume Builder OTP");
        message.setText("Your OTP is: " + otp + "\n\nThis code expires in 5 minutes.\n\nDo not share this with anyone.");
        mailSender.send(message);
    }

    public boolean verifyOtp(String email, String otp) {
        long[] entry = otpStore.get(email.toLowerCase());
        if (entry == null) return false;
        boolean valid = String.valueOf((long) entry[0]).equals(otp) && System.currentTimeMillis() < entry[1];
        if (valid) otpStore.remove(email.toLowerCase()); // single-use
        return valid;
    }
}
