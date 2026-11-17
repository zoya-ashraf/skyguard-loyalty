package com.example.loyalty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
public class SmsService {
    private final LoyaltyRepository repo;

    @Value("${loyalty.sms.mode}") private String mode;
    @Value("${loyalty.sms.country-code}") private String countryCode;
    @Value("${loyalty.sms.twilio-sid}") private String sid;
    @Value("${loyalty.sms.twilio-token}") private String token;
    @Value("${loyalty.sms.twilio-from}") private String from;

    public SmsService(LoyaltyRepository repo) {
        this.repo = repo;
    }

    public boolean isDemo() {
        return mode.equals("demo");
    }

    public void send(String phone, String text) {
        if (isDemo()) {
            repo.saveSms(phone, text);
            System.out.println("SMS to " + phone + ": " + text);
            return;
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("To", countryCode + phone);
            form.add("From", from);
            form.add("Body", text);
            RestClient.create().post()
                    .uri("https://api.twilio.com/2010-04-01/Accounts/" + sid + "/Messages.json")
                    .headers(h -> h.setBasicAuth(sid, token))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            System.out.println("Could not send SMS: " + e.getMessage());
        }
    }
}
