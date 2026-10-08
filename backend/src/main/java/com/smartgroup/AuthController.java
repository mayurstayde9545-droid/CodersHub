package com.smartgroup;

import jakarta.servlet.http.HttpServletRequest;
import java.security.SecureRandom;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record RegisterReq(String name, String email, String password, String role, String leaderCode) {}
    public record LoginReq(String email, String password, String role) {}

    private final JdbcTemplate db;
    private final BCryptPasswordEncoder enc = new BCryptPasswordEncoder();

    public AuthController(JdbcTemplate db) {
        this.db = db;
    }

    private static ResponseStatusException bad(String m) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, m);
    }

    /** Tells the login page whether a leader code already exists. */
    @GetMapping("/config")
    public Map<String, Object> config() {
        Integer c = db.queryForObject("SELECT COUNT(*) FROM settings WHERE k = 'leader_code'", Integer.class);
        return Map.of("hasLeaderCode", c != null && c > 0);
    }

    @PostMapping("/register")
    @Transactional
    public Map<String, Object> register(@RequestBody RegisterReq r) {
        String role = "leader".equals(r.role()) ? "leader" : "member";
        String name = r.name() == null ? "" : r.name().trim();
        String email = r.email() == null ? "" : r.email().trim().toLowerCase();
        if (name.isEmpty()) throw bad("Enter your full name.");
        if (!email.matches("^\\S+@\\S+\\.\\S+$")) throw bad("Enter a valid email address.");
        if (r.password() == null || r.password().length() < 8) throw bad("Password must be at least 8 characters.");
        Integer exists = db.queryForObject("SELECT COUNT(*) FROM users WHERE email = ?", Integer.class, email);
        if (exists != null && exists > 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists. Log in instead.");
        if (role.equals("leader")) checkLeaderCode(r.leaderCode());

        String hash = enc.encode(r.password());
        KeyHolder kh = new GeneratedKeyHolder();
        db.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                "INSERT INTO users(name, email, password_hash, role) VALUES (?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, hash);
            ps.setString(4, role);
            return ps;
        }, kh);
        long id = kh.getKey().longValue();

        if (role.equals("member")) { // every team member gets a member card automatically
            Long memberId = db.queryForObject(StateController.NEXT_ID, Long.class);
            db.update("INSERT INTO members(id, name, job_role, skills, exp_level, availability, user_id) VALUES (?,?,?,?,?,?,?)",
                memberId, name, "Team member", "", "Beginner", "Available", id);
        }
        return session(id, name, email, role);
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginReq r) {
        String email = r.email() == null ? "" : r.email().trim().toLowerCase();
        if (!email.matches("^\\S+@\\S+\\.\\S+$")) throw bad("Enter a valid email address.");
        var rows = db.queryForList("SELECT id, name, email, password_hash, role FROM users WHERE email = ?", email);
        if (rows.isEmpty() || r.password() == null || !enc.matches(r.password(), (String) rows.get(0).get("password_hash")))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password.");
        var u = rows.get(0);
        String role = (String) u.get("role");
        if (!role.equals(r.role()))
            throw bad("This is a " + ("leader".equals(role) ? "Team Leader" : "Team Member") + " account - switch the role above.");
        return session(((Number) u.get("id")).longValue(), (String) u.get("name"), (String) u.get("email"), role);
    }

    @GetMapping("/me")
    public AuthFilter.AuthUser me(HttpServletRequest req) {
        return (AuthFilter.AuthUser) req.getAttribute("user");
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest req) {
        db.update("DELETE FROM sessions WHERE token = ?", req.getAttribute("token"));
        return Map.of("ok", true);
    }

    private void checkLeaderCode(String code) {
        if (code == null || code.length() < 4) throw bad("Leader code must be at least 4 characters.");
        var saved = db.queryForList("SELECT v FROM settings WHERE k = 'leader_code'", String.class);
        if (saved.isEmpty()) { // first leader chooses the code
            db.update("INSERT INTO settings(k, v) VALUES ('leader_code', ?)", enc.encode(code));
        } else if (!enc.matches(code, saved.get(0))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Incorrect leader code.");
        }
    }


    private Map<String, Object> session(long id, String name, String email, String role) {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        String token = HexFormat.of().formatHex(b);
        db.update("INSERT INTO sessions(token, user_id) VALUES (?,?)", token, id);
        return Map.of("token", token, "user", Map.of("id", id, "name", name, "email", email, "role", role));
    }
}
