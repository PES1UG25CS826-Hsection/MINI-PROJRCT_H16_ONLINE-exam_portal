package edu.pes.exam.exam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.pes.exam.security.PortalPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/exams/{examId}/groups")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class ExamGroupController {
    private final JdbcTemplate jdbc;
    public ExamGroupController(JdbcTemplate jdbc) { this.jdbc=jdbc; }

    @GetMapping
    public List<Map<String,Object>> assignedGroups(@PathVariable UUID examId,@AuthenticationPrincipal PortalPrincipal user) {
        requireExamOwner(examId,user);
        return jdbc.query("SELECT g.id,g.name,g.description FROM exam_groups eg JOIN student_groups g ON g.id=eg.group_id WHERE eg.exam_id=? ORDER BY lower(g.name)",
                (rs,n)->Map.<String,Object>of("id",rs.getObject("id",UUID.class),"name",rs.getString("name"),"description",rs.getString("description")),examId);
    }

    @PutMapping("/{groupId}")
    @Transactional
    public Map<String,Object> assign(@PathVariable UUID examId,@PathVariable UUID groupId,@AuthenticationPrincipal PortalPrincipal user) {
        requireExamOwner(examId,user);
        requireDraft(examId);
        Integer groupCount=jdbc.queryForObject("SELECT COUNT(*) FROM student_groups g WHERE g.id=? AND (?='ADMIN' OR g.created_by=? OR EXISTS(SELECT 1 FROM users owner WHERE owner.id=g.created_by AND owner.role='ADMIN'))",Integer.class,groupId,user.role(),user.id());
        if(groupCount==null || groupCount==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Group not found.");
        jdbc.update("INSERT INTO exam_groups(exam_id,group_id) VALUES (?,?) ON CONFLICT DO NOTHING",examId,groupId);
        audit(user.id(),"EXAM_GROUP_ASSIGN",examId,groupId);
        return Map.of("examId",examId,"groupId",groupId,"assigned",true);
    }

    @DeleteMapping("/{groupId}")
    @Transactional
    public Map<String,Object> unassign(@PathVariable UUID examId,@PathVariable UUID groupId,@AuthenticationPrincipal PortalPrincipal user) {
        requireExamOwner(examId,user);
        requireDraft(examId);
        int removed=jdbc.update("DELETE FROM exam_groups WHERE exam_id=? AND group_id=?",examId,groupId);
        if(removed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Group assignment not found.");
        audit(user.id(),"EXAM_GROUP_UNASSIGN",examId,groupId);
        return Map.of("examId",examId,"groupId",groupId,"assigned",false);
    }

    private void requireExamOwner(UUID examId,PortalPrincipal user) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM exams WHERE id=? AND (?='ADMIN' OR created_by=?)",Integer.class,examId,user.role(),user.id());
        if(count==null || count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Exam not found.");
    }
    private void requireDraft(UUID examId) {
        String status=jdbc.queryForObject("SELECT status FROM exams WHERE id=?",String.class,examId);
        if(!"DRAFT".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Student groups can only be changed while the exam is a draft.");
    }
    private void audit(UUID actor,String action,UUID examId,UUID groupId) {
        jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id,details) VALUES (?,?,'EXAM',?,jsonb_build_object('groupId',?))",actor,action,examId.toString(),groupId.toString());
    }
}
