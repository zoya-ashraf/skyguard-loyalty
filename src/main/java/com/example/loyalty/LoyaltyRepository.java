package com.example.loyalty;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Repository
public class LoyaltyRepository {
    private final JdbcTemplate db;

    public LoyaltyRepository(JdbcTemplate db) {
        this.db = db;
    }

    private final RowMapper<Member> memberMapper = (rs, i) -> new Member(rs.getInt("id"), rs.getString("name"),
            rs.getString("phone"), rs.getString("home_city"), rs.getInt("points"), rs.getBoolean("frozen"),
            rs.getString("freeze_token"));

    private final RowMapper<InboxItem> itemMapper = (rs, i) -> new InboxItem(rs.getInt("id"), rs.getInt("amount"),
            rs.getBoolean("needs_face"), rs.getString("reason"), rs.getString("status"));

    private static final String ITEM_SELECT = "SELECT id, amount, needs_face, reason, "
            + "CASE WHEN status = 'PENDING' AND created_at < NOW() - INTERVAL 15 MINUTE THEN 'EXPIRED' ELSE status END AS status "
            + "FROM inbox ";

    public Member findByPhone(String phone) {
        List<Member> list = db.query("SELECT * FROM members WHERE phone = ?", memberMapper, phone);
        return list.isEmpty() ? null : list.get(0);
    }

    public Member findById(int id) {
        List<Member> list = db.query("SELECT * FROM members WHERE id = ?", memberMapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public Member findByFreezeToken(String token) {
        List<Member> list = db.query("SELECT * FROM members WHERE freeze_token = ?", memberMapper, token);
        return list.isEmpty() ? null : list.get(0);
    }

    public Member createMember(String name, String phone, String city) {
        String token = UUID.randomUUID().toString().replace("-", "");
        db.update("INSERT INTO members (name, phone, home_city, points, freeze_token) VALUES (?, ?, ?, 20000, ?)",
                name, phone, city, token);
        return findByPhone(phone);
    }

    public void setFrozen(int memberId, boolean frozen) {
        db.update("UPDATE members SET frozen = ? WHERE id = ?", frozen, memberId);
    }

    public int redemptionsThisWeek(int memberId) {
        return db.queryForObject("SELECT COUNT(*) FROM redemptions WHERE member_id = ? AND created_at > NOW() - INTERVAL 7 DAY",
                Integer.class, memberId);
    }

    public void addInbox(int memberId, int amount, boolean needsFace, String reason) {
        db.update("INSERT INTO inbox (member_id, amount, needs_face, reason) VALUES (?, ?, ?, ?)",
                memberId, amount, needsFace, reason);
    }

    public List<InboxItem> inbox(int memberId) {
        return db.query(ITEM_SELECT + "WHERE member_id = ? ORDER BY id DESC LIMIT 20", itemMapper, memberId);
    }

    public InboxItem inboxItem(int id, int memberId) {
        List<InboxItem> list = db.query(ITEM_SELECT + "WHERE id = ? AND member_id = ?", itemMapper, id, memberId);
        return list.isEmpty() ? null : list.get(0);
    }

    public void decline(int inboxId, int memberId) {
        db.update("UPDATE inbox SET status = 'DECLINED' WHERE id = ? AND member_id = ? AND status = 'PENDING'",
                inboxId, memberId);
    }

    @Transactional
    public void complete(int inboxId, int memberId, int amount) {
        int claimed = db.update("UPDATE inbox SET status = 'APPROVED' WHERE id = ? AND member_id = ? AND status = 'PENDING' "
                + "AND created_at > NOW() - INTERVAL 15 MINUTE", inboxId, memberId);
        if (claimed == 0) {
            throw new IllegalStateException("This request is no longer pending");
        }
        int paid = db.update("UPDATE members SET points = points - ? WHERE id = ? AND frozen = FALSE AND points >= ?",
                amount, memberId, amount);
        if (paid == 0) {
            throw new IllegalStateException("Not enough points, or the account is frozen");
        }
        db.update("INSERT INTO redemptions (member_id, amount) VALUES (?, ?)", memberId, amount);
    }

    public void saveSms(String phone, String message) {
        db.update("INSERT INTO sms_log (phone, message) VALUES (?, ?)", phone, message);
    }

    public List<String> recentSms(String phone) {
        return db.queryForList("SELECT message FROM sms_log WHERE phone = ? ORDER BY id DESC LIMIT 10", String.class, phone);
    }
}
