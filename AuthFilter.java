package com.smartgroup;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Adds CORS headers and requires a valid "Authorization: Bearer <token>" on every /api call except login/register/config. */
@Component
public class AuthFilter extends OncePerRequestFilter {

    public record AuthUser(long id, String name, String email, String role) {}

    private final JdbcTemplate db;

    public AuthFilter(JdbcTemplate db) {
        this.db = db;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        res.setHeader("Access-Control-Allow-Origin", "*");
        res.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        res.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        if ("OPTIONS".equals(req.getMethod())) {
            res.setStatus(200);
            return;
        }
        String path = req.getRequestURI();
        boolean open = path.equals("/api/auth/login") || path.equals("/api/auth/register")
                || path.equals("/api/auth/config") || !path.startsWith("/api/");
        if (open) {
            chain.doFilter(req, res);
            return;
        }
        String header = req.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            var rows = db.query(
                "SELECT u.id, u.name, u.email, u.role FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token = ?",
                (rs, i) -> new AuthUser(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)),
                token);
            if (!rows.isEmpty()) {
                req.setAttribute("user", rows.get(0));
                req.setAttribute("token", token);
                chain.doFilter(req, res);
                return;
            }
        }
        res.setStatus(401);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"Please log in again.\"}");
    }
}
