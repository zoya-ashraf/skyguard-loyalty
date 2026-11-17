package com.example.loyalty;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class RulesService {
    public enum Check { INBOX_ONLY, INBOX_AND_FACE, BLOCKED }

    public record Decision(Check check, String reason) {}

    private final int threshold;
    private final int weeklyLimit;

    public RulesService(@Value("${loyalty.threshold}") int threshold, @Value("${loyalty.weekly-limit}") int weeklyLimit) {
        this.threshold = threshold;
        this.weeklyLimit = weeklyLimit;
    }

    public Decision decide(boolean frozen, int amount, int redeemedThisWeek, RiskClient.RiskResult risk) {
        if (frozen) {
            return new Decision(Check.BLOCKED, "Account is frozen");
        }
        List<String> reasons = new ArrayList<>();
        if (amount >= threshold) {
            reasons.add("Large redemption");
        }
        if (redeemedThisWeek >= weeklyLimit) {
            reasons.add("Many redemptions this week");
        }
        if (risk.risk() >= 0.5) {
            reasons.add(risk.reason());
        }
        if (reasons.isEmpty()) {
            return new Decision(Check.INBOX_ONLY, "Looks normal");
        }
        return new Decision(Check.INBOX_AND_FACE, String.join(", ", reasons));
    }
}
