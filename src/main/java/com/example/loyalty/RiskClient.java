package com.example.loyalty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.Arrays;
import java.util.List;

@Component
public class RiskClient {
    public record RiskResult(double risk, int distanceKm, String reason) {}

    private final RestClient web;

    public RiskClient(@Value("${loyalty.risk-url}") String url) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(2000);
        this.web = RestClient.builder().baseUrl(url).requestFactory(factory).build();
    }

    public RiskResult check(String homeCity, String loginCity) {
        try {
            RiskResult result = web.get().uri("/risk?home={h}&login={l}", homeCity, loginCity)
                    .retrieve().body(RiskResult.class);
            return result == null ? backup() : result;
        } catch (Exception e) {
            return backup();
        }
    }

    public List<String> cities() {
        try {
            String[] list = web.get().uri("/cities").retrieve().body(String[].class);
            return Arrays.asList(list);
        } catch (Exception e) {
            return List.of();
        }
    }

    private RiskResult backup() {
        return new RiskResult(0.5, -1, "Risk checker is not reachable");
    }
}
