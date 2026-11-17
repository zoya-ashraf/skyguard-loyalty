package com.example.loyalty;

import org.springframework.stereotype.Service;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OtpService {
    static class Otp {
        String code;
        String city;
        String name;
        long sentAt;
        long expiresAt;
        int tries;
    }

    private final Map<String, Otp> codes = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final SmsService sms;

    public OtpService(SmsService sms) {
        this.sms = sms;
    }

    public void send(String phone, String city, String name) {
        long now = System.currentTimeMillis();
        Otp old = codes.get(phone);
        if (old != null && now - old.sentAt < 30000) {
            throw new IllegalStateException("Please wait 30 seconds before asking for a new code");
        }
        Otp otp = new Otp();
        otp.code = String.format("%06d", random.nextInt(1000000));
        otp.city = city;
        otp.name = name;
        otp.sentAt = now;
        otp.expiresAt = now + 5 * 60 * 1000;
        codes.put(phone, otp);
        sms.send(phone, "Your SkyGuard code is " + otp.code + ". It is valid for 5 minutes. Do not share it with anyone.");
    }

    public Otp check(String phone, String code) {
        if (phone == null || code == null) {
            return null;
        }
        Otp otp = codes.get(phone);
        if (otp == null || System.currentTimeMillis() > otp.expiresAt) {
            return null;
        }
        otp.tries++;
        if (otp.tries > 3) {
            codes.remove(phone);
            return null;
        }
        if (!otp.code.equals(code.trim())) {
            return null;
        }
        codes.remove(phone);
        return otp;
    }
}
