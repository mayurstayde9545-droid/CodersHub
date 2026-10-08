package com.smartgroup;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

/**
 * The frontend keeps the whole team (members, projects, tasks) in one object.
 * GET returns it from the MySQL tables; PUT saves it back into those tables.
 * Leaders can change everything. Members can only change the status of their own tasks.
 */
@RestController
@RequestMapping("/api/state")
public class StateController {

    public static final String NEXT_ID =
        "SELECT GREATEST(COALESCE((SELECT MAX(id) FROM members),0), COALESCE((SELECT MAX(id) FROM projects),0), COALESCE((SELECT MAX(id) FROM tasks),0)) + 1";

    public record Member(long id, String name, String role, List<String> skills, String exp, String av, String uid) {}
    public record Project(long id, String name, String desc, List<String> stack, String start, String deadline, String status, int progress) {}
    public record Task(long id, String title, long as, String pr, String st, String dl, long pj, List<String> sk) {}
    public record State(List<Member> m, List<Project> p, List<Task> t, long n) {}

    private final JdbcTemplate db;

    public StateController(JdbcTemplate db) {
        this.db = db;
    }

    private static List<String> list(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }
    private static String join(List<String> l) { return l == null ? "" : String.join(",", l); }
    private static java.sql.Date date(String s) { return s == null || s.isBlank() ? null : java.sql.Date.valueOf(s); }
    private static String str(java.sql.Date d) { return d == null ? "" : d.toString(); }
    private static <T> List<T> nz(List<T> l) { return l == null ? List.of() : l; }

    @GetMapping
    public State load() {
        var members = db.query("SELECT * FROM members ORDER BY id", (rs, i) -> {
            long u = rs.getLong("user_id");
            String uid = rs.wasNull() ? null : String.valueOf(u);
            return new Member(rs.getLong("id"), rs.getString("name"), rs.getString("job_role"), list(rs.getString("skills")),
                rs.getString("exp_level"), rs.getString("availability"), uid);
        });
        var projects = db.query("SELECT * FROM projects ORDER BY id", (rs, i) ->
            new Project(rs.getLong("id"), rs.getString("name"), rs.getString("description"), list(rs.getString("tech_stack")),
                str(rs.getDate("start_date")), str(rs.getDate("deadline")), rs.getString("status"), rs.getInt("progress")));
        var tasks = db.query("SELECT * FROM tasks ORDER BY id", (rs, i) ->
            new Task(rs.getLong("id"), rs.getString("title"), rs.getLong("assignee_id"), rs.getString("priority"),
                rs.getString("status"), str(rs.getDate("deadline")), rs.getLong("project_id"), list(rs.getString("skills"))));
        Long next = db.queryForObject(NEXT_ID, Long.class);
        return new State(members, projects, tasks, next == null ? 1 : next);
    }

    @PutMapping
    @Transactional
    public State save(@RequestBody State in, HttpServletRequest req) {
        var user = (AuthFilter.AuthUser) req.getAttribute("user");

        if (!"leader".equals(user.role())) { // team members: only the status of their own tasks
            var mine = db.queryForList("SELECT id FROM members WHERE user_id = ?", Long.class, user.id());
            if (!mine.isEmpty()) {
                long memberId = mine.get(0);
                for (Task t : nz(in.t()))
                    if (t.as() == memberId && List.of("todo", "doing", "done").contains(t.st()))
                        db.update("UPDATE tasks SET status = ? WHERE id = ? AND assignee_id = ?", t.st(), t.id(), memberId);
            }
            return load();
        }

        Set<Long> memberIds = new HashSet<>(), projectIds = new HashSet<>(), taskIds = new HashSet<>();
        for (Member x : nz(in.m())) {
            Long uid = x.uid() == null || x.uid().isBlank() ? null : Long.valueOf(x.uid());
            db.update("INSERT INTO members(id, name, job_role, skills, exp_level, availability, user_id) VALUES (?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE name=VALUES(name), job_role=VALUES(job_role), skills=VALUES(skills), "
                + "exp_level=VALUES(exp_level), availability=VALUES(availability), user_id=VALUES(user_id)",
                x.id(), x.name(), x.role(), join(x.skills()), x.exp(), x.av(), uid);
            memberIds.add(x.id());
        }
        for (Project x : nz(in.p())) {
            db.update("INSERT INTO projects(id, name, description, tech_stack, start_date, deadline, status, progress) VALUES (?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE name=VALUES(name), description=VALUES(description), tech_stack=VALUES(tech_stack), "
                + "start_date=VALUES(start_date), deadline=VALUES(deadline), status=VALUES(status), progress=VALUES(progress)",
                x.id(), x.name(), x.desc(), join(x.stack()), date(x.start()), date(x.deadline()), x.status(), x.progress());
            projectIds.add(x.id());
        }
        for (Task x : nz(in.t())) {
            if (!projectIds.contains(x.pj())) continue; // task of a deleted project
            Long assignee = memberIds.contains(x.as()) ? x.as() : null;
            db.update("INSERT INTO tasks(id, title, assignee_id, priority, status, deadline, project_id, skills) VALUES (?,?,?,?,?,?,?,?) "
                + "ON DUPLICATE KEY UPDATE title=VALUES(title), assignee_id=VALUES(assignee_id), priority=VALUES(priority), "
                + "status=VALUES(status), deadline=VALUES(deadline), project_id=VALUES(project_id), skills=VALUES(skills)",
                x.id(), x.title(), assignee, x.pr(), x.st(), date(x.dl()), x.pj(), join(x.sk()));
            taskIds.add(x.id());
        }
        prune("tasks", taskIds);
        prune("projects", projectIds);
        prune("members", memberIds);
        return load();
    }

    private void prune(String table, Set<Long> keep) {
        if (keep.isEmpty()) {
            db.update("DELETE FROM " + table);
        } else {
            db.update("DELETE FROM " + table + " WHERE id NOT IN (" + "?,".repeat(keep.size() - 1) + "?)", keep.toArray());
        }
    }
}
