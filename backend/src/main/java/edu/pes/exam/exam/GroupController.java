package edu.pes.exam.exam;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import edu.pes.exam.security.PortalPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/groups")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class GroupController {
    public record GroupRequest(@NotBlank @Size(max=120) String name, @Size(max=500) String description) { }
    public record MemberRequest(@NotBlank @Email @Size(max=254) String email) { }

    private final JdbcTemplate jdbc;
    public GroupController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @GetMapping
    public List<Map<String,Object>> list(@AuthenticationPrincipal PortalPrincipal user) {
        String sql = "SELECT g.id,g.name,g.description,COUNT(gm.student_id) member_count FROM student_groups g LEFT JOIN group_members gm ON gm.group_id=g.id WHERE (?='ADMIN' OR g.created_by=? OR EXISTS(SELECT 1 FROM users owner WHERE owner.id=g.created_by AND owner.role='ADMIN')) GROUP BY g.id ORDER BY lower(g.name)";
        return jdbc.query(sql, (rs,n) -> Map.of("id",rs.getObject("id",UUID.class),"name",rs.getString("name"),"description",rs.getString("description"),"memberCount",rs.getLong("member_count")),user.role(),user.id());
    }

    @PostMapping
    public Map<String,Object> create(@Valid @RequestBody GroupRequest request,@AuthenticationPrincipal PortalPrincipal user) {
        String name=request.name().trim();
        if (name.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Group name is required.");
        Integer duplicate=jdbc.queryForObject("SELECT COUNT(*) FROM student_groups WHERE created_by=? AND lower(name)=lower(?)",Integer.class,user.id(),name);
        if (duplicate!=null && duplicate>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"You already have a group with this name.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO student_groups(id,name,description,created_by) VALUES (?,?,?,?)",id,name,request.description()==null?"":request.description().trim(),user.id());
        audit(user.id(),"GROUP_CREATE","GROUP",id.toString());
        return Map.of("id",id,"name",name,"description",request.description()==null?"":request.description().trim(),"memberCount",0);
    }

    @GetMapping("/{groupId}/members")
    public List<Map<String,Object>> members(@PathVariable UUID groupId,@AuthenticationPrincipal PortalPrincipal user) {
        requireOwned(groupId,user);
        return jdbc.query("SELECT u.id,u.full_name,u.email FROM group_members gm JOIN users u ON u.id=gm.student_id WHERE gm.group_id=? ORDER BY lower(u.full_name)",
                (rs,n)->Map.<String,Object>of("id",rs.getObject("id",UUID.class),"fullName",rs.getString("full_name"),"email",rs.getString("email")),groupId);
    }

    @PostMapping("/{groupId}/members")
    public Map<String,Object> addMember(@PathVariable UUID groupId,@Valid @RequestBody MemberRequest request,@AuthenticationPrincipal PortalPrincipal user) {
        requireOwned(groupId,user);
        String email=request.email().trim().toLowerCase(java.util.Locale.ROOT);
        var students=jdbc.query("SELECT id FROM users WHERE email=? AND role='STUDENT' AND is_active=TRUE",(rs,n)->rs.getObject("id",UUID.class),email);
        if(students.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"No active student account has that email.");
        jdbc.update("INSERT INTO group_members(group_id,student_id) VALUES (?,?) ON CONFLICT DO NOTHING",groupId,students.get(0));
        audit(user.id(),"GROUP_MEMBER_ADD","GROUP",groupId.toString());
        return Map.of("groupId",groupId,"studentId",students.get(0),"email",email,"added",true);
    }

    @DeleteMapping("/{groupId}/members/{studentId}")
    public Map<String,Object> removeMember(@PathVariable UUID groupId,@PathVariable UUID studentId,@AuthenticationPrincipal PortalPrincipal user) {
        requireOwned(groupId,user);
        int removed=jdbc.update("DELETE FROM group_members WHERE group_id=? AND student_id=?",groupId,studentId);
        if(removed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Group membership not found.");
        audit(user.id(),"GROUP_MEMBER_REMOVE","GROUP",groupId.toString());
        return Map.of("groupId",groupId,"studentId",studentId,"removed",true);
    }

    private void requireOwned(UUID groupId,PortalPrincipal user) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM student_groups g WHERE g.id=? AND (?='ADMIN' OR g.created_by=? OR EXISTS(SELECT 1 FROM users owner WHERE owner.id=g.created_by AND owner.role='ADMIN'))",Integer.class,groupId,user.role(),user.id());
        if(count==null || count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Group not found.");
    }
    private void audit(UUID actor,String action,String type,String entity) {
        jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id) VALUES (?,?,?,?)",actor,action,type,entity);
    }
}
