package edu.pes.exam.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pes.exam.security.TokenService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PortalService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PasswordEncoder passwords;
    private final TokenService tokens;
    private final AnswerGrader grader;

    public PortalService(JdbcTemplate jdbc, ObjectMapper json, PasswordEncoder passwords, TokenService tokens,AnswerGrader grader) {
        this.jdbc = jdbc; this.json = json; this.passwords = passwords; this.tokens = tokens; this.grader=grader;
    }

    public Map<String, Object> register(RegisterRequest request) {
        if(request==null || request.email()==null || request.password()==null || request.fullName()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Email, password, and name are required");
        String email = request.email().trim().toLowerCase();
        if (email.isBlank() || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$") || email.length() > 254)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid email address");
        if (request.password().length() < 10 || request.password().length() > 128)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be between 10 and 128 characters");
        if (request.fullName().isBlank() || request.fullName().length() > 160)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter your name");
        try {
            UUID id = jdbc.queryForObject("INSERT INTO users(email,password_hash,full_name,role) VALUES (?,?,?,'STUDENT') RETURNING id", UUID.class,
                    email, passwords.encode(request.password()), request.fullName().trim());
            return authResponse(id, "STUDENT", request.fullName().trim());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
    }

    public Map<String, Object> login(LoginRequest request) {
        if(request==null || request.email()==null || request.password()==null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Email and password are required");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT id,password_hash,full_name,role,is_active FROM users WHERE email=lower(?)", request.email().trim());
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.get(0).get("is_active"))
                || !passwords.matches(request.password(), String.valueOf(rows.get(0).get("password_hash"))))
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
        Map<String, Object> user = rows.get(0);
        return authResponse((UUID) user.get("id"), String.valueOf(user.get("role")), String.valueOf(user.get("full_name")));
    }

    private Map<String, Object> authResponse(UUID id, String role, String name) {
        return Map.of("accessToken", tokens.issue(id, role), "tokenType", "Bearer", "expiresIn", 43200,
                "user", Map.of("id", id.toString(), "name", name, "role", role));
    }

    public Map<String, List<Map<String, Object>>> exams(UUID studentId) {
        List<Map<String, Object>> items = jdbc.queryForList("""
            SELECT e.id::text AS id,e.title,e.description,e.starts_at,e.ends_at,e.duration_minutes,
                   (SELECT a.id::text FROM attempts a WHERE a.exam_id=e.id AND a.student_id=? AND a.status='IN_PROGRESS' ORDER BY a.attempt_number DESC LIMIT 1) AS active_attempt,
                   (SELECT a.id::text FROM attempts a WHERE a.exam_id=e.id AND a.student_id=? ORDER BY a.attempt_number DESC LIMIT 1) AS latest_attempt,
                   EXISTS(SELECT 1 FROM attempts a WHERE a.exam_id=e.id AND a.student_id=? AND a.status IN ('SUBMITTED','TIMED_OUT')) AS completed,
                   (SELECT count(*) FROM attempts a WHERE a.exam_id=e.id AND a.student_id=?) AS attempt_count
            FROM exams e JOIN exam_assignments ea ON ea.exam_id=e.id
            WHERE ea.student_id=? AND e.status='PUBLISHED' AND (e.ends_at IS NULL OR e.ends_at>now() OR EXISTS(SELECT 1 FROM attempts done WHERE done.exam_id=e.id AND done.student_id=? AND done.status IN ('SUBMITTED','TIMED_OUT')))
            ORDER BY e.starts_at NULLS FIRST,e.title
            """, studentId, studentId, studentId, studentId, studentId, studentId);
        var upcoming = new ArrayList<Map<String, Object>>();
        var available = new ArrayList<Map<String, Object>>();
        var completed = new ArrayList<Map<String, Object>>();
        for (var row : items) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("status", row.get("active_attempt") != null ? "IN_PROGRESS" : Boolean.TRUE.equals(row.get("completed")) ? "COMPLETED" : "AVAILABLE");
            if (row.get("active_attempt") != null) available.add(item);
            else if (Boolean.TRUE.equals(row.get("completed"))) completed.add(item);
            else if (row.get("starts_at") != null && ((java.time.OffsetDateTime) row.get("starts_at")).toInstant().isAfter(databaseNow())) upcoming.add(item);
            else available.add(item);
        }
        return Map.of("upcoming", upcoming, "available", available, "completed", completed);
    }

    @Transactional
    public Map<String, Object> start(UUID studentId, UUID examId) {
        // Lock only this student's exam slot so starts for different students remain concurrent.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",(rs,rowNum)->Boolean.TRUE,examId+":"+studentId);
        List<Map<String, Object>> exams = jdbc.queryForList("SELECT id,status,starts_at,ends_at,duration_minutes,max_attempts FROM exams WHERE id=?", examId);
        if (exams.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Exam not found");
        Map<String, Object> exam = exams.get(0);
        if (!"PUBLISHED".equals(exam.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "This exam is not available");
        if (jdbc.queryForObject("SELECT count(*) FROM exam_assignments WHERE exam_id=? AND student_id=?", Long.class, examId, studentId) == 0)
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This exam is not assigned to your account");
        OffsetTimes window = new OffsetTimes((java.time.OffsetDateTime) exam.get("starts_at"), (java.time.OffsetDateTime) exam.get("ends_at"), java.time.OffsetDateTime.ofInstant(databaseNow(), java.time.ZoneOffset.UTC));
        if (window.start() != null && window.now().isBefore(window.start())) throw new ResponseStatusException(HttpStatus.CONFLICT, "This exam has not started yet");
        if (window.end() != null && !window.now().isBefore(window.end())) throw new ResponseStatusException(HttpStatus.CONFLICT, "This exam has ended");
        if (jdbc.queryForObject("SELECT count(*) FROM exam_questions WHERE exam_id=?",Integer.class,examId)==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"This exam has no questions yet");
        List<Map<String, Object>> active = jdbc.queryForList("SELECT id::text AS id,started_at FROM attempts WHERE exam_id=? AND student_id=? AND status='IN_PROGRESS' ORDER BY attempt_number DESC LIMIT 1", examId, studentId);
        if (!active.isEmpty()) return attempt(studentId, UUID.fromString(String.valueOf(active.get(0).get("id"))));
        Integer count = jdbc.queryForObject("SELECT count(*) FROM attempts WHERE exam_id=? AND student_id=?", Integer.class, examId, studentId);
        if (count >= (Integer) exam.get("max_attempts")) throw new ResponseStatusException(HttpStatus.CONFLICT, "You have reached the attempt limit");
        UUID attemptId = jdbc.queryForObject("INSERT INTO attempts(exam_id,student_id,attempt_number) VALUES (?,?,?) RETURNING id", UUID.class,
                examId, studentId, count + 1);
        return attempt(studentId, attemptId);
    }

    public Map<String, Object> attempt(UUID studentId, UUID attemptId) {
        expireIfNecessary(attemptId, studentId);
        Map<String, Object> a = ownedAttempt(studentId, attemptId);
        List<Map<String, Object>> questions = jdbc.queryForList("""
            SELECT q.id::text AS id,q.prompt,q.question_type,q.options::text AS options,
                   COALESCE(eq.points_override,q.points) AS points,aa.answer::text AS answer,
                   COALESCE(aa.is_marked_for_review,FALSE) AS marked_for_review
            FROM attempts a JOIN exam_questions eq ON eq.exam_id=a.exam_id
            JOIN questions q ON q.id=eq.question_id
            LEFT JOIN attempt_answers aa ON aa.attempt_id=a.id AND aa.question_id=q.id
            WHERE a.id=? ORDER BY eq.position
            """, attemptId);
        for (Map<String,Object> question : questions) {
            question.put("options", sanitizeOptions(parseJson(question.get("options"))));
            question.put("answer", parseJson(question.get("answer")));
        }
        var result = new LinkedHashMap<String,Object>();
        result.put("id", attemptId.toString()); result.put("status", a.get("status")); result.put("startedAt", a.get("started_at").toString());
        result.put("deadline", deadline(a).toString()); result.put("serverTime", databaseNow().toString());
        result.put("remainingSeconds", Math.max(0, deadline(a).getEpochSecond() - databaseNow().getEpochSecond()));
        result.put("questions", questions);
        if (!"IN_PROGRESS".equals(a.get("status"))) result.put("result", resultFor(studentId, attemptId));
        return result;
    }

    @Transactional
    public Map<String, Object> saveAnswer(UUID studentId, UUID attemptId, UUID questionId, AnswerRequest request) {
        Map<String, Object> a = ownedAttempt(studentId, attemptId);
        ensureAnswerWindow(a);
        if (jdbc.queryForObject("SELECT count(*) FROM exam_questions WHERE exam_id=? AND question_id=?", Integer.class, a.get("exam_id"), questionId) == 0)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Question is not part of this attempt");
        String answer = request.answer() == null ? "null" : writeJson(request.answer());
        jdbc.update("""
            INSERT INTO attempt_answers(attempt_id,question_id,answer,is_marked_for_review,saved_at)
            VALUES (?,?,CAST(? AS jsonb),?,now())
            ON CONFLICT (attempt_id,question_id) DO UPDATE SET answer=EXCLUDED.answer,
              is_marked_for_review=EXCLUDED.is_marked_for_review,saved_at=now()
            """, attemptId, questionId, answer, request.markedForReview());
        return Map.of("saved", true, "savedAt", databaseNow().toString());
    }

    @Transactional
    public Map<String, Object> submit(UUID studentId, UUID attemptId) {
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT a.id,a.exam_id,a.student_id,a.attempt_number,a.status,a.started_at,a.submitted_at,e.duration_minutes,e.ends_at,e.negative_marking,e.passing_marks FROM attempts a JOIN exams e ON e.id=a.exam_id WHERE a.id=? AND a.student_id=? FOR UPDATE OF a", attemptId, studentId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attempt not found");
        Map<String,Object> a=rows.get(0);
        if (!"IN_PROGRESS".equals(a.get("status"))) return resultFor(studentId, attemptId);
        Instant now = databaseNow();
        if (now.isAfter(deadline(a).plusSeconds(10))) throw new ResponseStatusException(HttpStatus.CONFLICT, "The answer window has ended");
        return gradeLocked(studentId, attemptId, a, now);
    }

    @Transactional
    public Map<String, Object> result(UUID studentId, UUID attemptId) {
        expireIfNecessary(attemptId, studentId);
        Map<String,Object> a=ownedAttempt(studentId, attemptId);
        if ("IN_PROGRESS".equals(a.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "This attempt has not been submitted");
        return resultFor(studentId, attemptId);
    }

    @Transactional
    public void expireIfNecessary(UUID attemptId, UUID studentId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT a.id,a.exam_id,a.student_id,a.attempt_number,a.status,a.started_at,a.submitted_at,e.duration_minutes,e.ends_at,e.negative_marking,e.passing_marks FROM attempts a JOIN exams e ON e.id=a.exam_id WHERE a.id=? AND a.student_id=? AND a.status='IN_PROGRESS' FOR UPDATE OF a",attemptId,studentId);
        if (!rows.isEmpty()) {
            Map<String,Object> a=rows.get(0); Instant now=databaseNow();
            if (!now.isBefore(deadline(a))) gradeLocked(studentId,attemptId,a,now);
        }
    }

    private Map<String,Object> gradeLocked(UUID studentId, UUID attemptId, Map<String,Object> a, Instant now) {
        List<Map<String,Object>> rows=jdbc.queryForList("""
          SELECT q.id::text AS question_id,q.correct_answer::text AS correct_answer,
                 COALESCE(eq.points_override,q.points) AS points,aa.answer::text AS answer
          FROM exam_questions eq JOIN questions q ON q.id=eq.question_id
          LEFT JOIN attempt_answers aa ON aa.attempt_id=? AND aa.question_id=q.id
          WHERE eq.exam_id=? ORDER BY eq.position
          """,attemptId,a.get("exam_id"));
        var submissions=new ArrayList<AnswerGrader.QuestionSubmission>();
        var answerUpdates=new ArrayList<Object[]>();
        BigDecimal penaltyFactor=(BigDecimal)a.get("negative_marking");
        for (Map<String,Object> row:rows) {
            BigDecimal points=(BigDecimal)row.get("points");
            String answer=row.get("answer")==null?null:String.valueOf(row.get("answer"));
            String correctAnswer=String.valueOf(row.get("correct_answer"));
            submissions.add(new AnswerGrader.QuestionSubmission(points,answer,correctAnswer));
            boolean answered=answer!=null && !"null".equals(answer);
            boolean correct=answered && grader.isCorrect(answer,correctAnswer);
            BigDecimal award=correct?points:BigDecimal.ZERO;
            if (answered) answerUpdates.add(new Object[]{correct,award,attemptId,row.get("question_id")});
        }
        if(!answerUpdates.isEmpty()) jdbc.batchUpdate("UPDATE attempt_answers SET is_correct=?,awarded_points=? WHERE attempt_id=? AND question_id=CAST(? AS uuid)",answerUpdates);
        AnswerGrader.Grade grade;
        try { grade=grader.grade(submissions,penaltyFactor,(BigDecimal)a.get("passing_marks")); }
        catch(IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"This exam has no questions"); }
        var submittedAt=java.time.OffsetDateTime.ofInstant(now,java.time.ZoneOffset.UTC);
        jdbc.update("UPDATE attempts SET status=?,submitted_at=? WHERE id=?", now.isBefore(deadline(a))?"SUBMITTED":"TIMED_OUT",submittedAt,attemptId);
        jdbc.update("INSERT INTO results(attempt_id,score,max_score,passed,graded_at) VALUES (?,?,?,?,?) ON CONFLICT (attempt_id) DO NOTHING",attemptId,grade.score(),grade.maxScore(),grade.passed(),submittedAt);
        return resultFor(studentId,attemptId);
    }

    private Map<String,Object> resultFor(UUID studentId, UUID attemptId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT r.score,r.max_score,r.percentage,r.passed,r.graded_at,a.status,e.show_question_review FROM results r JOIN attempts a ON a.id=r.attempt_id JOIN exams e ON e.id=a.exam_id WHERE r.attempt_id=? AND a.student_id=?",attemptId,studentId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"This attempt has not been submitted");
        Map<String,Object> row=rows.get(0); var output=new LinkedHashMap<String,Object>();
        output.put("attemptId",attemptId.toString()); output.put("status",row.get("status")); output.put("score",row.get("score")); output.put("maxScore",row.get("max_score"));
        output.put("percentage",row.get("percentage")); output.put("passed",row.get("passed")); output.put("gradedAt",row.get("graded_at").toString());
        if (Boolean.TRUE.equals(row.get("show_question_review"))) {
            output.put("questions",jdbc.queryForList("""
                SELECT q.id::text AS question_id,q.prompt,q.options::text AS options,q.correct_answer::text AS correct_answer,
                       aa.answer::text AS answer,aa.is_correct,aa.awarded_points,aa.is_marked_for_review
                FROM attempts a JOIN exam_questions eq ON eq.exam_id=a.exam_id JOIN questions q ON q.id=eq.question_id
                LEFT JOIN attempt_answers aa ON aa.attempt_id=a.id AND aa.question_id=q.id
                WHERE a.id=? ORDER BY eq.position
                """,attemptId));
            @SuppressWarnings("unchecked") var review=(List<Map<String,Object>>)output.get("questions");
            for (var q:review) { q.put("options",parseJson(q.get("options")));q.put("correct_answer",parseJson(q.get("correct_answer")));q.put("answer",parseJson(q.get("answer"))); }
        }
        return output;
    }

    private Map<String,Object> ownedAttempt(UUID studentId, UUID attemptId) {
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT a.id,a.exam_id,a.student_id,a.attempt_number,a.status,a.started_at,a.submitted_at,e.duration_minutes,e.ends_at,e.negative_marking,e.passing_marks FROM attempts a JOIN exams e ON e.id=a.exam_id WHERE a.id=? AND a.student_id=?",attemptId,studentId);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Attempt not found"); return rows.get(0);
    }

    private void ensureAnswerWindow(Map<String,Object> attempt) {
        if(!"IN_PROGRESS".equals(attempt.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT,"This attempt is read-only");
        if(databaseNow().isAfter(deadline(attempt).plusSeconds(10))) throw new ResponseStatusException(HttpStatus.CONFLICT,"The answer window has ended");
    }
    private Instant deadline(Map<String,Object> a) {
        Instant started=((java.time.OffsetDateTime)a.get("started_at")).toInstant();
        Instant due=started.plusSeconds(((Number)a.get("duration_minutes")).longValue()*60);
        if(a.get("ends_at") instanceof java.time.OffsetDateTime end && end.toInstant().isBefore(due)) due=end.toInstant();
        return due;
    }
    private Instant databaseNow() { return jdbc.queryForObject("SELECT now()",java.time.OffsetDateTime.class).toInstant(); }
    private Object parseJson(Object value) { if(value==null || "null".equals(value)) return null; try{return json.readValue(String.valueOf(value),Object.class);}catch(Exception ex){return null;} }
    private Object sanitizeOptions(Object options) {
        if(options instanceof List<?> list) return list.stream().map(this::sanitizeOptions).toList();
        if(options instanceof Map<?,?> map) {
            var clean=new LinkedHashMap<String,Object>();
            for(var entry:map.entrySet()) {
                String key=String.valueOf(entry.getKey());
                if(List.of("correct","iscorrect","correctanswer","answerkey","explanation").contains(key.replaceAll("[^A-Za-z]","").toLowerCase())) continue;
                clean.put(key,sanitizeOptions(entry.getValue()));
            }
            return clean;
        }
        return options;
    }
    private String writeJson(Object value) { try{return json.writeValueAsString(value);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Answer must be valid JSON");} }
    private record OffsetTimes(java.time.OffsetDateTime start,java.time.OffsetDateTime end,java.time.OffsetDateTime now) {}

    public record RegisterRequest(String email,String password,String fullName) {}
    public record LoginRequest(String email,String password) {}
    public record AnswerRequest(Object answer,boolean markedForReview) {}
}
