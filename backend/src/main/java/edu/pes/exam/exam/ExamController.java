package edu.pes.exam.exam;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pes.exam.security.PortalPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
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
public class ExamController {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ExamScoringService scoring;

    public ExamController(JdbcTemplate jdbc, ObjectMapper json, ExamScoringService scoring) { this.jdbc = jdbc; this.json = json; this.scoring = scoring; }

    public record ExamRequest(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=160) String subject,
                              @Min(5) @Max(240) int durationMinutes, @NotNull Instant startsAt, @NotNull Instant endsAt,
                              @NotNull @Min(1) BigDecimal totalMarks, @NotNull @Min(0) BigDecimal passingMarks,
                              @Min(0) @Max(100) int negativeMarkPercent, boolean showAnswersAfterSubmit,
                              @Min(1) @Max(10) int maxAttempts, boolean randomizeQuestions, boolean randomizeOptions) { }
    public record QuestionRequest(@NotBlank @Size(max=4000) String prompt, @NotBlank String type,
                                  @NotEmpty List<@NotBlank @Size(max=500) String> options,
                                  @NotEmpty List<@NotBlank String> correctAnswers,
                                  @NotNull @Min(1) BigDecimal points) { }
    public record AnswerRequest(@NotNull List<String> answers, boolean markedForReview) { }
    private record ExamRow(UUID id, String title, String subject, String status, int duration,
                           Instant startsAt, Instant endsAt, BigDecimal total, BigDecimal pass,
                           int negative, int maxAttempts, boolean review) { }

    @GetMapping("/exams")
    public List<Map<String, Object>> exams(@AuthenticationPrincipal PortalPrincipal user) {
        if (user.role().equals("TEACHER")) {
            return jdbc.query("SELECT id,title,subject,status,duration_minutes,starts_at,ends_at,total_marks,passing_marks,negative_mark_percent,max_attempts,show_answers_after_submit FROM exams WHERE created_by=? ORDER BY created_at DESC",
                    (rs, n) -> examMap(new ExamRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("subject"), rs.getString("status"), rs.getInt("duration_minutes"), instant(rs.getTimestamp("starts_at")), instant(rs.getTimestamp("ends_at")), rs.getBigDecimal("total_marks"), rs.getBigDecimal("passing_marks"), rs.getInt("negative_mark_percent"), rs.getInt("max_attempts"), rs.getBoolean("show_answers_after_submit"))), user.id());
        }
        if (user.role().equals("ADMIN")) {
            return jdbc.query("SELECT id,title,subject,status,duration_minutes,starts_at,ends_at,total_marks,passing_marks,negative_mark_percent,max_attempts,show_answers_after_submit FROM exams ORDER BY created_at DESC",
                    (rs, n) -> examMap(new ExamRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("subject"), rs.getString("status"), rs.getInt("duration_minutes"), instant(rs.getTimestamp("starts_at")), instant(rs.getTimestamp("ends_at")), rs.getBigDecimal("total_marks"), rs.getBigDecimal("passing_marks"), rs.getInt("negative_mark_percent"), rs.getInt("max_attempts"), rs.getBoolean("show_answers_after_submit"))));
        }
        return jdbc.query("SELECT e.id,e.title,e.subject,e.status,e.duration_minutes,e.starts_at,e.ends_at,e.total_marks,e.passing_marks,e.negative_mark_percent,e.max_attempts,e.show_answers_after_submit FROM exams e WHERE e.status='PUBLISHED' ORDER BY e.starts_at",
                (rs, n) -> {
                    ExamRow e = new ExamRow(rs.getObject("id", UUID.class), rs.getString("title"), rs.getString("subject"), rs.getString("status"), rs.getInt("duration_minutes"), instant(rs.getTimestamp("starts_at")), instant(rs.getTimestamp("ends_at")), rs.getBigDecimal("total_marks"), rs.getBigDecimal("passing_marks"), rs.getInt("negative_mark_percent"), rs.getInt("max_attempts"), rs.getBoolean("show_answers_after_submit"));
                    String state = jdbc.queryForObject("SELECT CASE WHEN EXISTS(SELECT 1 FROM attempts WHERE exam_id=? AND student_id=? AND status IN ('SUBMITTED','TIMED_OUT')) THEN 'Completed' WHEN now() < ? THEN 'Upcoming' ELSE 'Available' END", String.class, e.id(), user.id(), Timestamp.from(e.startsAt()));
                    return Map.<String,Object>of("id",e.id(),"title",e.title(),"subject",e.subject(),"durationMinutes",e.duration(),"startsAt",e.startsAt(),"endsAt",e.endsAt(),"totalMarks",e.total(),"state",state);
                });
    }

    @PostMapping("/exams")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Map<String, Object> createExam(@Valid @RequestBody ExamRequest req, @AuthenticationPrincipal PortalPrincipal user) {
        validateExam(req);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO exams(id,created_by,title,subject,duration_minutes,starts_at,ends_at,total_marks,passing_marks,negative_mark_percent,show_answers_after_submit,max_attempts,randomize_questions,randomize_options,status) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'DRAFT')",
                id,user.id(),req.title().trim(),req.subject().trim(),req.durationMinutes(),Timestamp.from(req.startsAt()),Timestamp.from(req.endsAt()),req.totalMarks(),req.passingMarks(),req.negativeMarkPercent(),req.showAnswersAfterSubmit(),req.maxAttempts(),req.randomizeQuestions(),req.randomizeOptions());
        audit(user.id(),"EXAM_CREATE","EXAM",id.toString(),null);
        return Map.of("id",id,"status","DRAFT");
    }

    @PostMapping("/exams/{examId}/questions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Transactional
    public Map<String, Object> addQuestion(@PathVariable UUID examId, @Valid @RequestBody QuestionRequest req,
                                          @AuthenticationPrincipal PortalPrincipal user) {
        ExamRow exam = ownedExam(examId,user);
        if (!exam.status().equals("DRAFT")) throw conflict("Questions can only be added to a draft exam.");
        String type = req.type().toUpperCase();
        if (!Set.of("MCQ","MULTI_SELECT","TRUE_FALSE").contains(type)) throw bad("Question type must be MCQ, MULTI_SELECT, or TRUE_FALSE.");
        if (type.equals("TRUE_FALSE") && !req.options().equals(List.of("True","False"))) throw bad("True/False questions must have True and False options.");
        if (type.equals("MCQ") && (req.options().size()<2 || req.options().size()>6)) throw bad("Single-choice questions need 2 to 6 options.");
        if (type.equals("MULTI_SELECT") && (req.options().size()<2 || req.options().size()>6)) throw bad("Multi-select questions need 2 to 6 options.");
        if (new HashSet<>(req.options()).size()!=req.options().size() || !req.options().containsAll(req.correctAnswers())) throw bad("Options must be unique and every correct answer must be an option.");
        if (type.equals("MCQ") && req.correctAnswers().size()!=1) throw bad("Single-choice questions need exactly one correct answer.");
        if (type.equals("TRUE_FALSE") && req.correctAnswers().size()!=1) throw bad("True/False questions need exactly one correct answer.");
        UUID questionId=UUID.randomUUID();
        jdbc.update("INSERT INTO questions(id,author_id,prompt,question_type,options,correct_answer,points) VALUES (?,?,?, ?,?::jsonb,?::jsonb,?)",
                questionId,user.id(),req.prompt().trim(),type,stringify(req.options()),stringify(req.correctAnswers()),req.points());
        Integer pos=jdbc.queryForObject("SELECT COALESCE(MAX(position),-1)+1 FROM exam_questions WHERE exam_id=?",Integer.class,examId);
        jdbc.update("INSERT INTO exam_questions(exam_id,question_id,position) VALUES (?,?,?)",examId,questionId,pos);
        return Map.of("id",questionId,"position",pos);
    }

    @PostMapping("/exams/{examId}/publish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    @Transactional
    public Map<String,Object> publish(@PathVariable UUID examId,@AuthenticationPrincipal PortalPrincipal user) {
        ExamRow exam=ownedExam(examId,user);
        if (!exam.status().equals("DRAFT")) throw conflict("Only a draft exam can be published.");
        BigDecimal sum=jdbc.queryForObject("SELECT COALESCE(SUM(COALESCE(eq.points_override,q.points)),0) FROM exam_questions eq JOIN questions q ON q.id=eq.question_id WHERE eq.exam_id=?",BigDecimal.class,examId);
        if (sum==null || sum.signum()<=0) throw bad("Add at least one question before publishing.");
        if (sum.compareTo(exam.total())!=0) throw bad("Question marks must add up to the exam total of "+exam.total()+".");
        jdbc.update("UPDATE exams SET status='PUBLISHED',updated_at=now() WHERE id=?",examId);
        audit(user.id(),"EXAM_PUBLISH","EXAM",examId.toString(),null);
        return Map.of("id",examId,"status","PUBLISHED");
    }

    @PostMapping("/exams/{examId}/unpublish")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public Map<String,Object> unpublish(@PathVariable UUID examId,@AuthenticationPrincipal PortalPrincipal user) {
        ownedExam(examId,user);
        int n=jdbc.update("UPDATE exams SET status='DRAFT',updated_at=now() WHERE id=? AND status='PUBLISHED' AND NOT EXISTS(SELECT 1 FROM attempts WHERE exam_id=? AND status IN ('IN_PROGRESS','SUBMITTED','TIMED_OUT'))",examId,examId);
        if(n==0) throw conflict("This exam cannot be unpublished after a student has started it.");
        audit(user.id(),"EXAM_UNPUBLISH","EXAM",examId.toString(),null);
        return Map.of("id",examId,"status","DRAFT");
    }

    @PostMapping("/exams/{examId}/attempts")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public Map<String,Object> start(@PathVariable UUID examId,@AuthenticationPrincipal PortalPrincipal user,HttpServletRequest request) {
        ExamRow exam=exam(examId);
        Instant now=Instant.now();
        if(!exam.status().equals("PUBLISHED")) throw conflict("This exam is not available.");
        if(now.isBefore(exam.startsAt())) throw conflict("This exam has not started yet.");
        if(now.isAfter(exam.endsAt())) throw conflict("This exam has ended.");
        Integer used=jdbc.queryForObject("SELECT COUNT(*) FROM attempts WHERE exam_id=? AND student_id=? AND status IN ('IN_PROGRESS','SUBMITTED','TIMED_OUT')",Integer.class,examId,user.id());
        if(used!=null && used>=exam.maxAttempts()) throw conflict("The attempt limit has been reached.");
        int number=(used==null?0:used)+1;
        UUID attempt=UUID.randomUUID();
        jdbc.update("INSERT INTO attempts(id,exam_id,student_id,attempt_number,status,started_at) VALUES (?,?,?,?,'IN_PROGRESS',?)",attempt,examId,user.id(),number,Timestamp.from(now));
        audit(user.id(),"EXAM_START","ATTEMPT",attempt.toString(),request.getRemoteAddr());
        return attemptView(attempt,user,true);
    }

    @GetMapping("/attempts/{attemptId}")
    @PreAuthorize("hasRole('STUDENT')")
    public Map<String,Object> getAttempt(@PathVariable UUID attemptId,@AuthenticationPrincipal PortalPrincipal user) {
        return attemptView(attemptId,user,true);
    }

    @PutMapping("/attempts/{attemptId}/answers/{questionId}")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public Map<String,Object> saveAnswer(@PathVariable UUID attemptId,@PathVariable UUID questionId,
                                        @Valid @RequestBody AnswerRequest req,@AuthenticationPrincipal PortalPrincipal user) {
        var rows=jdbc.query("SELECT a.status,a.started_at,e.duration_minutes,e.ends_at FROM attempts a JOIN exams e ON e.id=a.exam_id WHERE a.id=? AND a.student_id=? FOR UPDATE OF a",
                (rs,n)->Map.<String,Object>of("status",rs.getString("status"),"started",rs.getTimestamp("started_at").toInstant(),"duration",rs.getInt("duration_minutes"),"ends",rs.getTimestamp("ends_at").toInstant()),attemptId,user.id());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attempt not found.");
        Map<String,Object> row=rows.get(0);
        if(!row.get("status").equals("IN_PROGRESS")) throw conflict("This attempt is already submitted.");
        Instant deadline=((Instant)row.get("started")).plusSeconds(60L*(int)row.get("duration"));
        Instant examEnd=(Instant)row.get("ends");
        if(Instant.now().isAfter(deadline) || Instant.now().isAfter(examEnd.plusSeconds(10))) throw conflict("The answer window has ended.");
        Integer linked=jdbc.queryForObject("SELECT COUNT(*) FROM attempts a JOIN exam_questions eq ON eq.exam_id=a.exam_id WHERE a.id=? AND eq.question_id=?",Integer.class,attemptId,questionId);
        if(linked==null || linked==0) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Question is not part of this attempt.");
        jdbc.update("INSERT INTO attempt_answers(attempt_id,question_id,answer,marked_for_review,saved_at) VALUES (?,?,?::jsonb,?,now()) ON CONFLICT(attempt_id,question_id) DO UPDATE SET answer=EXCLUDED.answer,marked_for_review=EXCLUDED.marked_for_review,saved_at=now()",
                attemptId,questionId,stringify(req.answers()),req.markedForReview());
        return Map.of("saved",true,"savedAt",Instant.now());
    }

    @PostMapping("/attempts/{attemptId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    @Transactional
    public Map<String,Object> submit(@PathVariable UUID attemptId,@AuthenticationPrincipal PortalPrincipal user,HttpServletRequest request) {
        var attemptRows=jdbc.query("SELECT a.exam_id,a.status,a.started_at,a.submitted_at,e.duration_minutes,e.ends_at,e.total_marks,e.passing_marks,e.negative_mark_percent FROM attempts a JOIN exams e ON e.id=a.exam_id WHERE a.id=? AND a.student_id=? FOR UPDATE OF a",
                (rs,n)->new Object[]{rs.getObject("exam_id",UUID.class),rs.getString("status"),rs.getTimestamp("started_at").toInstant(),rs.getTimestamp("submitted_at") == null ? null : rs.getTimestamp("submitted_at").toInstant(),rs.getInt("duration_minutes"),rs.getTimestamp("ends_at").toInstant(),rs.getBigDecimal("total_marks"),rs.getBigDecimal("passing_marks"),rs.getInt("negative_mark_percent")},attemptId,user.id());
        if(attemptRows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attempt not found.");
        Object[] a=attemptRows.get(0);
        UUID examId=(UUID)a[0];
        var prior=jdbc.query("SELECT score,max_score,percentage,passed,graded_at FROM results WHERE attempt_id=?",(rs,n)->resultMap(rs),attemptId);
        if(!prior.isEmpty()) return prior.get(0);
        if(!a[1].equals("IN_PROGRESS")) throw conflict("This attempt is already submitted.");
        int neg=(int)a[8];
        BigDecimal max=(BigDecimal)a[6];
        List<ScoreLine> lines=jdbc.query("SELECT COALESCE(eq.points_override,q.points) points,q.correct_answer::text correct,aa.answer::text answer FROM exam_questions eq JOIN questions q ON q.id=eq.question_id LEFT JOIN attempt_answers aa ON aa.attempt_id=? AND aa.question_id=q.id WHERE eq.exam_id=? ORDER BY eq.position",
                (rs,n)->new ScoreLine(rs.getBigDecimal("points"),rs.getString("correct"),rs.getString("answer")),attemptId,examId);
        List<BigDecimal> questionScores=new ArrayList<>();
        for(ScoreLine line:lines){
            Set<String> expected=parseStrings(line.correct());
            Set<String> supplied=line.answer()==null?Set.of():parseStrings(line.answer());
            questionScores.add(scoring.score(line.points(),expected,supplied,neg));
        }
        BigDecimal score=scoring.total(questionScores);
        BigDecimal passing=(BigDecimal)a[7];
        boolean passed=score.compareTo(passing)>=0;
        Instant submitted=Instant.now();
        jdbc.update("UPDATE attempts SET status='SUBMITTED',submitted_at=? WHERE id=?",Timestamp.from(submitted),attemptId);
        UUID resultId=UUID.randomUUID();
        jdbc.update("INSERT INTO results(id,attempt_id,score,max_score,passed,graded_by) VALUES (?,?,?,?,?,?)",resultId,attemptId,score,max,passed,null);
        audit(user.id(),"EXAM_SUBMIT","ATTEMPT",attemptId.toString(),request.getRemoteAddr());
        return Map.of("id",resultId,"attemptId",attemptId,"score",score,"maxScore",max,"percentage",score.multiply(BigDecimal.valueOf(100)).divide(max,2,RoundingMode.HALF_UP),"passed",passed,"submittedAt",submitted);
    }

    @GetMapping("/results")
    @PreAuthorize("hasAnyRole('STUDENT','TEACHER','ADMIN')")
    public List<Map<String,Object>> results(@AuthenticationPrincipal PortalPrincipal user) {
        String sql=user.role().equals("TEACHER")
                ? "SELECT r.id,r.attempt_id,e.title,e.subject,r.score,r.max_score,r.percentage,r.passed,r.graded_at,u.email student_email FROM results r JOIN attempts a ON a.id=r.attempt_id JOIN exams e ON e.id=a.exam_id JOIN users u ON u.id=a.student_id WHERE e.created_by=? ORDER BY r.graded_at DESC"
                : user.role().equals("ADMIN")
                ? "SELECT r.id,r.attempt_id,e.title,e.subject,r.score,r.max_score,r.percentage,r.passed,r.graded_at,u.email student_email FROM results r JOIN attempts a ON a.id=r.attempt_id JOIN exams e ON e.id=a.exam_id JOIN users u ON u.id=a.student_id ORDER BY r.graded_at DESC"
                : "SELECT r.id,r.attempt_id,e.title,e.subject,r.score,r.max_score,r.percentage,r.passed,r.graded_at FROM results r JOIN attempts a ON a.id=r.attempt_id JOIN exams e ON e.id=a.exam_id WHERE a.student_id=? ORDER BY r.graded_at DESC";
        return user.role().equals("ADMIN")?jdbc.query(sql,(rs,n)->resultMap(rs)):jdbc.query(sql,(rs,n)->resultMap(rs),user.role().equals("TEACHER")?user.id():user.id());
    }

    @GetMapping("/reports/exams/{examId}.csv")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public org.springframework.http.ResponseEntity<String> reportCsv(@PathVariable UUID examId,@AuthenticationPrincipal PortalPrincipal user) {
        ownedExam(examId,user);
        List<Map<String,Object>> rows=jdbc.query("SELECT u.full_name,u.email,r.score,r.max_score,r.percentage,r.passed,r.graded_at FROM results r JOIN attempts a ON a.id=r.attempt_id JOIN users u ON u.id=a.student_id WHERE a.exam_id=? ORDER BY r.graded_at",(rs,n)->Map.of("name",rs.getString("full_name"),"email",rs.getString("email"),"score",rs.getBigDecimal("score"),"max",rs.getBigDecimal("max_score"),"percentage",rs.getBigDecimal("percentage"),"passed",rs.getBoolean("passed"),"date",rs.getTimestamp("graded_at").toInstant()),examId);
        StringBuilder csv=new StringBuilder("Name,Email,Score,Maximum,Percentage,Passed,Submitted at\r\n");
        for(var r:rows) csv.append(csv(r.get("name"))).append(',').append(csv(r.get("email"))).append(',').append(r.get("score")).append(',').append(r.get("max")).append(',').append(r.get("percentage")).append(',').append(r.get("passed")).append(',').append(r.get("date")).append("\r\n");
        return org.springframework.http.ResponseEntity.ok().header("Content-Type","text/csv; charset=utf-8").header("Content-Disposition","attachment; filename=exam-results.csv").body(csv.toString());
    }

    private Map<String,Object> attemptView(UUID attempt,PortalPrincipal user,boolean hideAnswers) {
        var rows=jdbc.query("SELECT a.exam_id,a.status,a.started_at,a.submitted_at,e.title,e.duration_minutes,e.ends_at,e.show_answers_after_submit,e.randomize_questions,e.randomize_options FROM attempts a JOIN exams e ON e.id=a.exam_id WHERE a.id=? AND a.student_id=?",(rs,n)->Map.<String,Object>of("examId",rs.getObject("exam_id",UUID.class),"status",rs.getString("status"),"startedAt",rs.getTimestamp("started_at").toInstant(),"submittedAt",rs.getTimestamp("submitted_at")==null?Instant.EPOCH:rs.getTimestamp("submitted_at").toInstant(),"title",rs.getString("title"),"duration",rs.getInt("duration_minutes"),"endsAt",rs.getTimestamp("ends_at").toInstant(),"showAnswers",rs.getBoolean("show_answers_after_submit"),"randomizeQuestions",rs.getBoolean("randomize_questions"),"randomizeOptions",rs.getBoolean("randomize_options")),attempt,user.id());
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attempt not found.");
        Map<String,Object> a=rows.get(0);
        UUID examId=(UUID)a.get("examId");
        boolean submitted=!a.get("status").equals("IN_PROGRESS");
        List<Map<String,Object>> questions=jdbc.query("SELECT q.id,q.prompt,q.question_type,q.options::text,COALESCE(eq.points_override,q.points) points,aa.answer::text,COALESCE(aa.marked_for_review,FALSE) marked FROM exam_questions eq JOIN questions q ON q.id=eq.question_id LEFT JOIN attempt_answers aa ON aa.attempt_id=? AND aa.question_id=q.id WHERE eq.exam_id=? ORDER BY eq.position",(rs,n)->{
            UUID questionId=rs.getObject("id",UUID.class);
            List<String> choices=new ArrayList<>(parseStrings(rs.getString("options")));
            if(Boolean.TRUE.equals(a.get("randomizeOptions"))) java.util.Collections.shuffle(choices,new java.util.Random(attempt.hashCode()^questionId.hashCode()));
            Map<String,Object> q=new java.util.LinkedHashMap<>();q.put("id",questionId);q.put("prompt",rs.getString("prompt"));q.put("type",rs.getString("question_type"));q.put("options",choices);q.put("points",rs.getBigDecimal("points"));q.put("answer",rs.getString("answer")==null?List.of():parseStrings(rs.getString("answer")));q.put("markedForReview",rs.getBoolean("marked"));
            if(submitted && Boolean.TRUE.equals(a.get("showAnswers"))) q.put("correctAnswers",jdbc.queryForObject("SELECT correct_answer::text FROM questions WHERE id=?",String.class,q.get("id")));
            return q;
        },attempt,examId);
        if(Boolean.TRUE.equals(a.get("randomizeQuestions"))) java.util.Collections.shuffle(questions,new java.util.Random(attempt.hashCode()));
        Instant expires=((Instant)a.get("startedAt")).plusSeconds(60L*(int)a.get("duration"));
        return Map.of("id",attempt,"examTitle",a.get("title"),"status",a.get("status"),"startedAt",a.get("startedAt"),"expiresAt",expires,"questions",questions);
    }

    private ExamRow ownedExam(UUID id,PortalPrincipal user) {
        ExamRow e=exam(id);
        if(!user.role().equals("ADMIN") && !jdbc.queryForObject("SELECT COUNT(*) FROM exams WHERE id=? AND created_by=?",Integer.class,id,user.id()).equals(1)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"You cannot manage this exam.");
        return e;
    }
    private ExamRow exam(UUID id) {
        var rows=jdbc.query("SELECT id,title,subject,status,duration_minutes,starts_at,ends_at,total_marks,passing_marks,negative_mark_percent,max_attempts,show_answers_after_submit FROM exams WHERE id=?",(rs,n)->new ExamRow(rs.getObject("id",UUID.class),rs.getString("title"),rs.getString("subject"),rs.getString("status"),rs.getInt("duration_minutes"),instant(rs.getTimestamp("starts_at")),instant(rs.getTimestamp("ends_at")),rs.getBigDecimal("total_marks"),rs.getBigDecimal("passing_marks"),rs.getInt("negative_mark_percent"),rs.getInt("max_attempts"),rs.getBoolean("show_answers_after_submit")),id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Exam not found.");
        return rows.get(0);
    }
    private void validateExam(ExamRequest e) {
        if(!e.startsAt().isBefore(e.endsAt())) throw bad("The exam end must be after the start.");
        if(e.passingMarks().compareTo(e.totalMarks())>0) throw bad("Passing marks cannot exceed total marks.");
    }
    private Map<String,Object> examMap(ExamRow e) {
        Map<String,Object> map=new java.util.LinkedHashMap<>();
        map.put("id",e.id());map.put("title",e.title());map.put("subject",e.subject());map.put("status",e.status());
        map.put("durationMinutes",e.duration());map.put("startsAt",e.startsAt());map.put("endsAt",e.endsAt());
        map.put("totalMarks",e.total());map.put("passingMarks",e.pass());map.put("negativeMarkPercent",e.negative());
        map.put("maxAttempts",e.maxAttempts());map.put("showAnswersAfterSubmit",e.review());return map;
    }
    private Map<String,Object> resultMap(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String,Object> r=new java.util.LinkedHashMap<>();r.put("id",rs.getObject("id",UUID.class));r.put("attemptId",rs.getObject("attempt_id",UUID.class));r.put("examTitle",rs.getString("title"));r.put("subject",rs.getString("subject"));r.put("score",rs.getBigDecimal("score"));r.put("maxScore",rs.getBigDecimal("max_score"));r.put("percentage",rs.getBigDecimal("percentage"));r.put("passed",rs.getBoolean("passed"));r.put("gradedAt",rs.getTimestamp("graded_at").toInstant());try{r.put("studentEmail",rs.getString("student_email"));}catch(java.sql.SQLException ignored){}return r;
    }
    private void audit(UUID actor,String action,String type,String entity,String ip) {
        if(ip==null) jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id) VALUES (?,?,?,?)",actor,action,type,entity);
        else jdbc.update("INSERT INTO audit_log(actor_id,action,entity_type,entity_id,ip_address) VALUES (?,?,?,?,?::inet)",actor,action,type,entity,ip);
    }
    private String stringify(Object value) { try{return json.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalArgumentException("Invalid JSON data.",e);} }
    private Set<String> parseStrings(String value) { try{return new HashSet<>(json.readValue(value,new TypeReference<List<String>>(){}));}catch(Exception e){throw new IllegalStateException("Stored answer data is invalid.",e);} }
    private Instant instant(Timestamp value){return value==null?null:value.toInstant();}
    private ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
    private ResponseStatusException conflict(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private String csv(Object value){String s=String.valueOf(value);return "\""+s.replace("\"","\"\"")+"\"";}
    private record ScoreLine(BigDecimal points,String correct,String answer){}
}
