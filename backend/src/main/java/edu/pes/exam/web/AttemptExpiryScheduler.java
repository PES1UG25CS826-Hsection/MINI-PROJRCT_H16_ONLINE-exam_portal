package edu.pes.exam.web;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AttemptExpiryScheduler {
    private final JdbcTemplate jdbc;
    private final PortalService portal;
    public AttemptExpiryScheduler(JdbcTemplate jdbc,PortalService portal){this.jdbc=jdbc;this.portal=portal;}

    @Scheduled(fixedDelayString="${exam.expiry-scan-ms:5000}")
    public void submitExpiredAttempts() {
        var expired=jdbc.query("""
            SELECT a.id,a.student_id FROM attempts a JOIN exams e ON e.id=a.exam_id
            WHERE a.status='IN_PROGRESS' AND
              (a.started_at + make_interval(mins => e.duration_minutes) <= now() OR e.ends_at <= now())
            ORDER BY a.started_at LIMIT 200
            """,(rs,n)->new ExpiredAttempt(rs.getObject("id",UUID.class),rs.getObject("student_id",UUID.class)));
        for(var attempt:expired) {
            try { portal.expireIfNecessary(attempt.id(),attempt.studentId()); }
            catch(RuntimeException ignored) { /* A later scan retries transient database failures. */ }
        }
    }
    private record ExpiredAttempt(UUID id,UUID studentId){}
}
