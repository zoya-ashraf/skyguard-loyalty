package com.example.loyalty;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class LoyaltyController {
    public record SendBody(String phone, String city, String name) {}
    public record VerifyBody(String phone, String code) {}
    public record CodeBody(String code) {}
    public record AmountBody(int amount) {}
    public record FaceBody(String result) {}

    private final LoyaltyRepository repo;
    private final RulesService rules;
    private final RiskClient risk;
    private final OtpService otp;
    private final SmsService sms;

    @Value("${loyalty.public-url}") private String publicUrl;

    public LoyaltyController(LoyaltyRepository repo, RulesService rules, RiskClient risk, OtpService otp, SmsService sms) {
        this.repo = repo;
        this.rules = rules;
        this.risk = risk;
        this.otp = otp;
        this.sms = sms;
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private Member me(HttpSession session) {
        Object id = session.getAttribute("memberId");
        Member m = id == null ? null : repo.findById((Integer) id);
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in");
        }
        return m;
    }

    private String freezeLink(Member m) {
        return publicUrl + "/api/freeze/" + m.freezeToken();
    }

    private Map<String, Object> profile(Member m, HttpSession session) {
        return Map.of("name", m.name(), "phone", m.phone(), "homeCity", m.homeCity(),
                "loginCity", session.getAttribute("city"), "points", m.points(), "frozen", m.frozen());
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        return Map.of("demo", sms.isDemo(), "cities", risk.cities());
    }

    @PostMapping("/otp/send")
    public Map<String, Object> sendCode(@RequestBody SendBody b) {
        String phone = b.phone() == null ? "" : b.phone().trim();
        if (!phone.matches("[6-9][0-9]{9}")) {
            throw bad("Enter a valid 10 digit mobile number");
        }
        if (b.city() == null || b.city().isBlank()) {
            throw bad("Choose a city");
        }
        String name = null;
        if (repo.findByPhone(phone) == null) {
            if (b.name() == null || b.name().isBlank()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This number is not registered. Tick 'I am new' to sign up.");
            }
            name = b.name().trim();
            if (name.length() > 60) {
                throw bad("Name is too long");
            }
            if (!risk.cities().contains(b.city())) {
                throw bad("Pick a city from the list");
            }
        }
        try {
            otp.send(phone, b.city(), name);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        }
        return Map.of("sent", true);
    }

    @PostMapping("/otp/verify")
    public Map<String, Object> verifyCode(@RequestBody VerifyBody b, HttpServletRequest request) {
        OtpService.Otp o = otp.check(b.phone(), b.code());
        if (o == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong or expired code");
        }
        Member m = repo.findByPhone(b.phone());
        if (m == null) {
            m = repo.createMember(o.name, b.phone(), o.city);
        }
        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session.setAttribute("memberId", m.id());
        session.setAttribute("city", o.city);
        if (risk.check(m.homeCity(), o.city).risk() >= 0.5) {
            sms.send(m.phone(), "SkyGuard: new login from " + o.city + ". Not you? Freeze your account: " + freezeLink(m));
        }
        return profile(m, session);
    }

    @GetMapping("/me")
    public Map<String, Object> getMe(HttpSession session) {
        return profile(me(session), session);
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("ok", true);
    }

    @PostMapping("/redeem")
    public Map<String, Object> redeem(@RequestBody AmountBody b, HttpSession session) {
        Member m = me(session);
        if (b.amount() <= 0 || b.amount() > m.points()) {
            throw bad("Enter an amount within your balance");
        }
        RiskClient.RiskResult r = risk.check(m.homeCity(), (String) session.getAttribute("city"));
        RulesService.Decision d = rules.decide(m.frozen(), b.amount(), repo.redemptionsThisWeek(m.id()), r);
        if (d.check() == RulesService.Check.BLOCKED) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account is frozen. Unfreeze it to redeem points.");
        }
        repo.addInbox(m.id(), b.amount(), d.check() == RulesService.Check.INBOX_AND_FACE, d.reason());
        sms.send(m.phone(), "SkyGuard: request to redeem " + b.amount() + " points. Approve it in the app. Not you? Freeze now: " + freezeLink(m));
        return Map.of("ok", true);
    }

    @GetMapping("/inbox")
    public List<InboxItem> inbox(HttpSession session) {
        return repo.inbox(me(session).id());
    }

    private InboxItem pendingItem(int id, Member m) {
        InboxItem item = repo.inboxItem(id, m.id());
        if (item == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Request not found");
        }
        if (!item.status().equals("PENDING")) {
            throw bad("This request is no longer pending");
        }
        return item;
    }

    private Map<String, Object> finish(InboxItem item, Member m) {
        try {
            repo.complete(item.id(), m.id(), item.amount());
        } catch (IllegalStateException e) {
            throw bad(e.getMessage());
        }
        int balance = repo.findById(m.id()).points();
        sms.send(m.phone(), "SkyGuard: " + item.amount() + " points redeemed. Balance: " + balance + ". Not you? Freeze now: " + freezeLink(m));
        return Map.of("balance", balance);
    }

    @PostMapping("/inbox/{id}/approve")
    public Map<String, Object> approve(@PathVariable int id, HttpSession session) {
        Member m = me(session);
        InboxItem item = pendingItem(id, m);
        if (item.needsFace()) {
            throw bad("This request needs face unlock");
        }
        return finish(item, m);
    }

    @PostMapping("/inbox/{id}/face")
    public Map<String, Object> face(@PathVariable int id, @RequestBody FaceBody b, HttpSession session) {
        Member m = me(session);
        InboxItem item = pendingItem(id, m);
        if (!item.needsFace()) {
            throw bad("Face unlock is not needed for this request");
        }
        if ("pass".equals(b.result())) {
            return finish(item, m);
        }
        repo.setFrozen(m.id(), true);
        sms.send(m.phone(), "SkyGuard: face check failed, so your account was frozen. Unfreeze it in the app with an SMS code.");
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Face did not match. Your account has been frozen.");
    }

    @PostMapping("/inbox/{id}/decline")
    public Map<String, Object> decline(@PathVariable int id, HttpSession session) {
        Member m = me(session);
        repo.decline(id, m.id());
        return Map.of("ok", true);
    }

    @PostMapping("/freeze")
    public Map<String, Object> freeze(HttpSession session) {
        Member m = me(session);
        repo.setFrozen(m.id(), true);
        sms.send(m.phone(), "SkyGuard: your account is frozen. Unfreeze it in the app with an SMS code.");
        return Map.of("ok", true);
    }

    @GetMapping(value = "/freeze/{token}", produces = "text/html")
    public String freezeFromSms(@PathVariable String token) {
        Member m = repo.findByFreezeToken(token);
        if (m == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "This freeze link is not valid");
        }
        repo.setFrozen(m.id(), true);
        sms.send(m.phone(), "SkyGuard: your account is frozen. Unfreeze it in the app with an SMS code.");
        return "<body style='font-family:sans-serif;text-align:center;padding:60px'><h2>Account frozen</h2>"
                + "<p>No points can be redeemed now. Log in to the app with an SMS code to unfreeze it.</p></body>";
    }

    @PostMapping("/unfreeze/send")
    public Map<String, Object> sendUnfreezeCode(HttpSession session) {
        Member m = me(session);
        try {
            otp.send(m.phone(), "", null);
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
        }
        return Map.of("sent", true);
    }

    @PostMapping("/unfreeze")
    public Map<String, Object> unfreeze(@RequestBody CodeBody b, HttpSession session) {
        Member m = me(session);
        if (otp.check(m.phone(), b.code()) == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong or expired code");
        }
        repo.setFrozen(m.id(), false);
        sms.send(m.phone(), "SkyGuard: your account has been unfrozen.");
        return Map.of("ok", true);
    }

    @GetMapping("/demo/sms")
    public List<String> demoSms(@RequestParam String phone) {
        if (!sms.isDemo()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Not available");
        }
        return repo.recentSms(phone);
    }
}
