package com.smartgroup;

import java.sql.Timestamp;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Email ownership verification and a single welcome/project-summary message. */
@RestController
@RequestMapping("/api/email")
public class EmailController {
    public record EmailReq(String email) {}
    public record VerifyReq(String email, String code) {}
    public record WelcomeReq(String email, String report, String verificationToken) {}

    private final JdbcTemplate db;
    private final JavaMailSender mail;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();
    private final String from;
    private final String username;

    public EmailController(JdbcTemplate db, JavaMailSender mail, @Value("${app.mail.from:}") String from,
            @Value("${spring.mail.username:}") String username) {
        this.db = db;
        this.mail = mail;
        this.from = from;
        this.username = username;
    }

    private static String normalize(String raw) {
        String email = raw == null ? "" : raw.trim().toLowerCase();
        if (email.length() > 190 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address.");
        return email;
    }

    private void deliver(String to, String subject, String body) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            String sender = from != null && !from.isBlank() ? from.trim()
                : username == null || username.isBlank() ? null : username.trim();
            if (sender == null) throw new IllegalStateException("Set MAIL_FROM or MAIL_USERNAME.");
            message.setFrom(sender);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mail.send(message);
        } catch (MailException | IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Email could not be sent. Check the mail server settings and try again.");
        }
    }

    @PostMapping("/verification/request")
    @Transactional
    public Map<String, Object> requestCode(@RequestBody EmailReq request) {
        String email = normalize(request.email());
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT last_sent_at FROM email_verifications WHERE email = ?", email);
        if (!rows.isEmpty()) {
            Timestamp last = (Timestamp) rows.get(0).get("last_sent_at");
            if (last != null && last.toInstant().isAfter(Instant.now().minusSeconds(60)))
                throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Wait one minute before requesting another verification code.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        deliver(email, "SmartGroup email verification code",
            "Your SmartGroup verification code is " + code + ". It expires in 15 minutes. If you did not request it, ignore this email.");
        String hash = encoder.encode(code);
        Timestamp now = Timestamp.from(Instant.now());
        Timestamp expiry = Timestamp.from(Instant.now().plusSeconds(15 * 60));
        db.update("INSERT INTO email_verifications(email, code_hash, expires_at, last_sent_at, attempts, verified_at, welcome_sent) "
                + "VALUES(?,?,?,?,0,NULL,FALSE) ON DUPLICATE KEY UPDATE code_hash=VALUES(code_hash), "
                + "expires_at=VALUES(expires_at), last_sent_at=VALUES(last_sent_at), attempts=0, verified_at=NULL",
            email, hash, expiry, now);
        return Map.of("message", "A verification code was sent to your email.");
    }

    @PostMapping("/verification/confirm")
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public Map<String, Object> verify(@RequestBody VerifyReq request) {
        String email = normalize(request.email());
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT code_hash, expires_at, attempts, verified_at FROM email_verifications WHERE email = ?", email);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request a verification code first.");
        Map<String, Object> row = rows.get(0);
        if (row.get("verified_at") != null) return Map.of("verified", true);
        Timestamp expiry = (Timestamp) row.get("expires_at");
        String code = request.code() == null ? "" : request.code().trim();
        int attempts = ((Number) row.get("attempts")).intValue();
        if (attempts >= 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
            "Too many incorrect codes. Request a new verification code.");
        if (!code.matches("[0-9]{6}")) {
            db.update("UPDATE email_verifications SET attempts = attempts + 1 WHERE email = ?", email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the six-digit code sent to your email.");
        }
        if (expiry == null || expiry.toInstant().isBefore(Instant.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code is incorrect or expired. Request a new code and try again.");
        if (!encoder.matches(code, (String) row.get("code_hash"))) {
            db.update("UPDATE email_verifications SET attempts = attempts + 1 WHERE email = ?", email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That code is incorrect or expired. Request a new code and try again.");
        }
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);
        String token = HexFormat.of().formatHex(tokenBytes);
        db.update("UPDATE email_verifications SET verified_at = ?, verification_token_hash = ? WHERE email = ?",
            Timestamp.from(Instant.now()), encoder.encode(token), email);
        return Map.of("verified", true, "verificationToken", token);
    }

    @PostMapping("/welcome")
    @Transactional
    public Map<String, Object> welcome(@RequestBody WelcomeReq request) {
        String email = normalize(request.email());
        List<Map<String, Object>> rows = db.queryForList(
            "SELECT verified_at, verification_token_hash, welcome_sent FROM email_verifications WHERE email = ?", email);
        if (rows.isEmpty() || rows.get(0).get("verified_at") == null)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email before requesting the welcome message.");
        String token = request.verificationToken() == null ? "" : request.verificationToken();
        if (!encoder.matches(token, (String) rows.get(0).get("verification_token_hash")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your email before requesting the welcome message.");
        Object welcomeSent = rows.get(0).get("welcome_sent");
        if (Boolean.TRUE.equals(welcomeSent) || welcomeSent instanceof Number n && n.intValue() != 0)
            return Map.of("sent", false, "message", "The welcome report was already sent.");
        String report = request.report() == null ? "" : request.report().trim();
        if (report.length() > 10_000) report = report.substring(0, 10_000);
        deliver(email, "You are now part of the SmartGroup team", report.isBlank()
            ? "Welcome to the SmartGroup team. Sign in to see the latest project status and tasks."
            : "Welcome to the SmartGroup team. Here is your current project report:\n\n" + report);
        db.update("UPDATE email_verifications SET welcome_sent = TRUE WHERE email = ? AND welcome_sent = FALSE", email);
        return Map.of("sent", true, "message", "Your welcome message and project report were sent.");
    }
}
