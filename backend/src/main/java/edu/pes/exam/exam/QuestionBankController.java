package edu.pes.exam.exam;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pes.exam.security.PortalPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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
@RequestMapping("/api")
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class QuestionBankController {
    public record QuestionRequest(@NotBlank @Size(max=4000) String prompt, @NotBlank String type,
                                  @NotEmpty List<@NotBlank @Size(max=500) String> options,
                                  @NotEmpty List<@NotBlank String> correctAnswers,
                                  @NotNull @jakarta.validation.constraints.DecimalMin("0.01") java.math.BigDecimal points) { }
    public record LinkRequest(@NotNull @jakarta.validation.constraints.DecimalMin("0.01") java.math.BigDecimal pointsOverride) { }

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public QuestionBankController(JdbcTemplate jdbc,ObjectMapper json) { this.jdbc=jdbc;this.json=json; }

    @GetMapping("/questions")
    public List<Map<String,Object>> list(@AuthenticationPrincipal PortalPrincipal user) {
        String sql="SELECT id,prompt,question_type,options::text,correct_answer::text,points FROM questions WHERE (?='ADMIN' OR author_id=?) ORDER BY created_at DESC";
        return jdbc.query(sql,(rs,n)->Map.<String,Object>of("id",rs.getObject("id",UUID.class),"prompt",rs.getString("prompt"),"type",rs.getString("question_type"),"options",readList(rs.getString("options")),"correctAnswers",readList(rs.getString("correct_answer")),"points",rs.getBigDecimal("points")),user.role(),user.id());
    }

    @PostMapping("/questions")
    @Transactional
    public Map<String,Object> create(@Valid @RequestBody QuestionRequest request,@AuthenticationPrincipal PortalPrincipal user) {
        validate(request);
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO questions(id,author_id,prompt,question_type,options,correct_answer,points) VALUES (?,?,?,?,?::jsonb,?::jsonb,?)",id,user.id(),request.prompt().trim(),request.type().toUpperCase(java.util.Locale.ROOT),write(request.options()),write(request.correctAnswers()),request.points());
        audit(user.id(),"QUESTION_CREATE",id);
        return Map.of("id",id,"prompt",request.prompt().trim(),"type",request.type().toUpperCase(java.util.Locale.ROOT),"options",request.options(),"correctAnswers",request.correctAnswers(),"points",request.points());
    }

    @PutMapping("/questions/{questionId}")
    @Transactional
    public Map<String,Object> update(@PathVariable UUID questionId,@Valid @RequestBody QuestionRequest request,@AuthenticationPrincipal PortalPrincipal user) {
        validate(request);
        requireQuestionOwner(questionId,user);
        Integer locked=jdbc.queryForObject("SELECT COUNT(*) FROM exam_questions eq JOIN exams e ON e.id=eq.exam_id WHERE eq.question_id=? AND (e.status='PUBLISHED' OR EXISTS(SELECT 1 FROM attempts a WHERE a.exam_id=e.id))",Integer.class,questionId);
        if(locked!=null && locked>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"A question used by a published exam or attempt cannot be changed.");
        jdbc.update("UPDATE questions SET prompt=?,question_type=?,options=?::jsonb,correct_answer=?::jsonb,points=?,updated_at=now() WHERE id=?",request.prompt().trim(),request.type().toUpperCase(java.util.Locale.ROOT),write(request.options()),write(request.correctAnswers()),request.points(),questionId);
        audit(user.id(),"QUESTION_UPDATE",questionId);
        return Map.of("id",questionId,"updated",true);
    }

    @DeleteMapping("/questions/{questionId}")
    @Transactional
    public Map<String,Object> delete(@PathVariable UUID questionId,@AuthenticationPrincipal PortalPrincipal user) {
        requireQuestionOwner(questionId,user);
        Integer used=jdbc.queryForObject("SELECT COUNT(*) FROM exam_questions WHERE question_id=?",Integer.class,questionId);
        if(used!=null && used>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Remove this question from its draft exams before deleting it.");
        jdbc.update("DELETE FROM questions WHERE id=?",questionId);
        audit(user.id(),"QUESTION_DELETE",questionId);
        return Map.of("id",questionId,"deleted",true);
    }

    @PostMapping("/exams/{examId}/question-bank/{questionId}")
    @Transactional
    public Map<String,Object> addToExam(@PathVariable UUID examId,@PathVariable UUID questionId,@Valid @RequestBody LinkRequest request,@AuthenticationPrincipal PortalPrincipal user) {
        requireExamOwner(examId,user);
        String status=jdbc.queryForObject("SELECT status FROM exams WHERE id=?",String.class,examId);
        if(!"DRAFT".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Questions can only be selected for a draft exam.");
        requireQuestionOwner(questionId,user);
        Integer duplicate=jdbc.queryForObject("SELECT COUNT(*) FROM exam_questions WHERE exam_id=? AND question_id=?",Integer.class,examId,questionId);
        if(duplicate!=null && duplicate>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"This question is already in the exam.");
        Integer position=jdbc.queryForObject("SELECT COALESCE(MAX(position),-1)+1 FROM exam_questions WHERE exam_id=?",Integer.class,examId);
        jdbc.update("INSERT INTO exam_questions(exam_id,question_id,position,points_override) VALUES (?,?,?,?)",examId,questionId,position,request.pointsOverride());
        audit(user.id(),"QUESTION_ASSIGN",questionId);
        return Map.of("examId",examId,"questionId",questionId,"position",position);
    }

    @DeleteMapping("/exams/{examId}/questions/{questionId}")
    @Transactional
    public Map<String,Object> removeFromExam(@PathVariable UUID examId,@PathVariable UUID questionId,@AuthenticationPrincipal PortalPrincipal user) {
        requireExamOwner(examId,user);
        String status=jdbc.queryForObject("SELECT status FROM exams WHERE id=?",String.class,examId);
        if(!"DRAFT".equals(status)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Questions can only be removed from a draft exam.");
        int removed=jdbc.update("DELETE FROM exam_questions WHERE exam_id=? AND question_id=? AND EXISTS(SELECT 1 FROM questions WHERE id=? AND (?='ADMIN' OR author_id=?))",examId,questionId,questionId,user.role(),user.id());
        if(removed==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Question assignment not found.");
        audit(user.id(),"QUESTION_UNASSIGN",questionId);
        return Map.of("examId",examId,"questionId",questionId,"removed",true);
    }

    private void validate(QuestionRequest request) {
        String type=request.type().trim().toUpperCase(java.util.Locale.ROOT);
        if(!Set.of("MCQ","MULTI_SELECT","TRUE_FALSE").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Question type must be MCQ, MULTI_SELECT, or TRUE_FALSE.");
        if(new HashSet<>(request.options()).size()!=request.options().size() || new HashSet<>(request.correctAnswers()).size()!=request.correctAnswers().size() || !request.options().containsAll(request.correctAnswers())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Options and correct answers must be unique, and every correct answer must be an option.");
        if(type.equals("TRUE_FALSE") && !request.options().equals(List.of("True","False"))) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"True/False questions must have True and False options.");
        if(!type.equals("TRUE_FALSE") && (request.options().size()<2 || request.options().size()>6)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Multiple-choice questions need 2 to 6 options.");
        if(!type.equals("MULTI_SELECT") && request.correctAnswers().size()!=1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Single-choice and True/False questions need exactly one correct answer.");
    }
    private void requireQuestionOwner(UUID id,PortalPrincipal user) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM questions WHERE id=? AND (?='ADMIN' OR author_id=?)",Integer.class,id,user.role(),user.id());
        if(count==null || count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Question not found.");
    }
    private void requireExamOwner(UUID id,PortalPrincipal user) {
        Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM exams WHERE id=? AND (?='ADMIN' OR created_by=?)",Integer.class,id,user.role(),user.id());
        if(count==null || count==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Exam not found.");
    }
    private List<String> readList(String value) { try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){throw new IllegalStateException("Stored question JSON is invalid.",e);} }
    private String write(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Question data could not be serialized.",e);} }
    private void audit(UUID actor,String action,UUID questionId) { jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id) VALUES (?,?, 'QUESTION', ?)",actor,action,questionId.toString()); }
}
