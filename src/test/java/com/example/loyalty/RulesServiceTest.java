package com.example.loyalty;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RulesServiceTest {
    private final RulesService rules = new RulesService(5000, 5);
    private final RiskClient.RiskResult safe = new RiskClient.RiskResult(0.1, 0, "Login from home area");
    private final RiskClient.RiskResult risky = new RiskClient.RiskResult(0.8, 1148, "Login from Delhi");

    @Test
    void frozenAccountIsBlocked() {
        assertEquals(RulesService.Check.BLOCKED, rules.decide(true, 100, 0, safe).check());
    }

    @Test
    void smallSafeRequestOnlyNeedsInbox() {
        assertEquals(RulesService.Check.INBOX_ONLY, rules.decide(false, 100, 0, safe).check());
    }

    @Test
    void bigAmountNeedsFace() {
        assertEquals(RulesService.Check.INBOX_AND_FACE, rules.decide(false, 5000, 0, safe).check());
    }

    @Test
    void tooManyRedemptionsNeedsFace() {
        assertEquals(RulesService.Check.INBOX_AND_FACE, rules.decide(false, 100, 5, safe).check());
    }

    @Test
    void riskyLoginNeedsFace() {
        assertEquals(RulesService.Check.INBOX_AND_FACE, rules.decide(false, 100, 0, risky).check());
    }

    @Test
    void reasonIsReturned() {
        assertEquals("Login from Delhi", rules.decide(false, 100, 0, risky).reason());
    }
}
