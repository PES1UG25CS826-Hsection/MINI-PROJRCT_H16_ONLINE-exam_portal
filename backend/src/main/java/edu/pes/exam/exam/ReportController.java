package edu.pes.exam.exam;

import java.util.Map;
import java.util.UUID;

import edu.pes.exam.security.PortalPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class ReportController {
    private final JdbcTemplate jdbc;
    public ReportController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping("/exams/{examId}")
    public Map<String,Object> examReport(@PathVariable UUID examId,@AuthenticationPrincipal PortalPrincipal user) {
        Integer owned=jdbc.queryForObject("SELECT COUNT(*) FROM exams WHERE id=? AND (?='ADMIN' OR created_by=?)",Integer.class,examId,user.role(),user.id());
        if(owned==null || owned==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Exam not found.");
        return jdbc.queryForObject("SELECT COUNT(r.id) attempts,COALESCE(AVG(r.score),0) average,COALESCE(MAX(r.score),0) highest,COALESCE(MIN(r.score),0) lowest,COALESCE(100.0*AVG(CASE WHEN r.passed THEN 1.0 ELSE 0.0 END),0) pass_percentage FROM results r JOIN attempts a ON a.id=r.attempt_id WHERE a.exam_id=?",
                (rs,n)->Map.of("attempts",rs.getLong("attempts"),"average",rs.getBigDecimal("average"),"highest",rs.getBigDecimal("highest"),"lowest",rs.getBigDecimal("lowest"),"passPercentage",rs.getBigDecimal("pass_percentage")),examId);
    }
}
