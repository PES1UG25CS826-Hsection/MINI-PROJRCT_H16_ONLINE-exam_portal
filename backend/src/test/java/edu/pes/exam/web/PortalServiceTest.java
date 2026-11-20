package edu.pes.exam.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class PortalServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ObjectMapper json = new ObjectMapper();
    private final PortalService service = new PortalService(jdbc, json, new AnswerGrader(json));
    private final UUID studentId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();
    private final UUID attemptId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private OffsetDateTime now;

    @BeforeEach
    void useFixedDatabaseTime() {
        now = OffsetDateTime.of(2026, 11, 20, 12, 0, 0, 0, ZoneOffset.UTC);
        when(jdbc.queryForObject("SELECT now()", OffsetDateTime.class)).thenReturn(now);
    }

    @Test
    void groupsAssignedExamsByScheduleAndAttemptState() {
        Map<String, Object> active = exam("active", null, false);
        active.put("active_attempt", "attempt-1");
        Map<String, Object> completed = exam("completed", null, true);
        Map<String, Object> upcoming = exam("upcoming", now.plusHours(1), false);
        when(jdbc.queryForList(startsWith("SELECT DISTINCT e.id"), any(Object[].class)))
                .thenReturn(List.of(active, completed, upcoming, exam("available", now.minusHours(1), false)));

        Map<String, List<Map<String, Object>>> groups = service.exams(studentId);

        assertEquals(1, groups.get("upcoming").size());
        assertEquals("upcoming", groups.get("upcoming").get(0).get("id"));
        assertEquals(2, groups.get("available").size());
        assertEquals("IN_PROGRESS", groups.get("available").get(0).get("status"));
        assertEquals(1, groups.get("completed").size());
        assertEquals("COMPLETED", groups.get("completed").get(0).get("status"));
    }

    @Test
    void startRejectsAnExamThatIsNotAssignedToTheStudent() {
        when(jdbc.queryForObject(contains("pg_advisory_xact_lock"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Boolean.TRUE);
        Map<String, Object> exam = new HashMap<>();
        exam.put("status", "PUBLISHED");
        exam.put("starts_at", now.minusHours(1));
        exam.put("ends_at", now.plusHours(1));
        exam.put("duration_minutes", 60);
        exam.put("max_attempts", 1);
        when(jdbc.queryForList(startsWith("SELECT id,status,starts_at"), any(Object[].class)))
                .thenReturn(List.of(exam));
        when(jdbc.queryForObject(startsWith("SELECT count(*) FROM exam_groups"), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.start(studentId, examId));

        assertEquals(HttpStatus.FORBIDDEN, error.getStatusCode());
    }

    @Test
    void attemptRestoresAnswersAndStripsAnswerKeyFieldsFromOptions() {
        mockAttemptReads("IN_PROGRESS");
        Map<String, Object> question = questionRow("\"A\"");
        question.put("options", "[{\"text\":\"A\",\"correct\":true,\"explanation\":\"secret\"},{\"text\":\"B\"}]");
        when(jdbc.queryForList(contains("SELECT q.id::text AS id"), any(Object[].class))).thenReturn(List.of(question));

        Map<String, Object> view = service.attempt(studentId, attemptId);

        assertEquals(attemptId.toString(), view.get("id"));
        assertTrue((Long) view.get("remainingSeconds") > 0);
        @SuppressWarnings("unchecked")
        Map<String, Object> restored = (Map<String, Object>) ((List<?>) view.get("questions")).get(0);
        assertEquals("A", restored.get("answer"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> options = (List<Map<String, Object>>) restored.get("options");
        assertEquals(Map.of("text", "A"), options.get(0));
        assertFalse(view.containsKey("result"));
    }

    @Test
    void saveAnswerPersistsJsonAndReviewFlag() {
        mockOwnedAttemptRead("IN_PROGRESS");
        when(jdbc.queryForObject(startsWith("SELECT count(*) FROM exam_questions"), eq(Integer.class), any(Object[].class)))
                .thenReturn(1);
        var request = new PortalService.AnswerRequest(Map.of("choice", "A"), true);

        Map<String, Object> result = service.saveAnswer(studentId, attemptId, questionId, request);

        assertEquals(Boolean.TRUE, result.get("saved"));
        verify(jdbc).update(contains("INSERT INTO attempt_answers"), eq(attemptId), eq(questionId),
                eq("{\"choice\":\"A\"}"), eq(true));
    }

    @Test
    void submitGradesOnceAndReturnsPersistedResult() {
        Map<String, Object> attempt = attemptRow("IN_PROGRESS");
        Map<String, Object> question = questionRow(null);
        Map<String, Object> persisted = resultRow("SUBMITTED", false);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FOR UPDATE OF a")) return List.of(attempt);
            if (sql.contains("SELECT q.id::text AS question_id")) return List.of(question);
            if (sql.startsWith("SELECT r.score")) return List.of(persisted);
            return List.of();
        });

        Map<String, Object> result = service.submit(studentId, attemptId);

        assertEquals(attemptId.toString(), result.get("attemptId"));
        assertEquals(new BigDecimal("0.00"), result.get("score"));
        assertEquals(new BigDecimal("2.00"), result.get("maxScore"));
        verify(jdbc).update(startsWith("UPDATE attempts SET status=?"), eq("SUBMITTED"), any(OffsetDateTime.class), eq(attemptId));
        verify(jdbc).update(startsWith("INSERT INTO results"), eq(attemptId), eq(new BigDecimal("0.00")),
                eq(new BigDecimal("2.00")), eq(false), any(OffsetDateTime.class));
    }

    @Test
    void resubmittingACompletedAttemptReturnsExistingResult() {
        Map<String, Object> attempt = attemptRow("SUBMITTED");
        Map<String, Object> persisted = resultRow("SUBMITTED", true);
        Map<String, Object> review = new HashMap<>();
        review.put("question_id", questionId.toString());
        review.put("prompt", "Choose A");
        review.put("options", "[{\"text\":\"A\"}]");
        review.put("correct_answer", "\"A\"");
        review.put("answer", "\"A\"");
        review.put("is_correct", true);
        review.put("awarded_points", new BigDecimal("2.00"));
        review.put("marked_for_review", false);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("FOR UPDATE OF a")) return List.of(attempt);
            if (sql.startsWith("SELECT r.score")) return List.of(persisted);
            if (sql.contains("SELECT q.id::text AS question_id,q.prompt")) return List.of(review);
            return List.of();
        });

        Map<String, Object> result = service.submit(studentId, attemptId);

        assertEquals(new BigDecimal("2.00"), result.get("score"));
        @SuppressWarnings("unchecked")
        Map<String, Object> answerReview = (Map<String, Object>) ((List<?>) result.get("questions")).get(0);
        assertEquals("A", answerReview.get("correct_answer"));
        assertEquals("A", answerReview.get("answer"));
    }

    @Test
    void resultRejectsAnAttemptThatHasNotBeenSubmitted() {
        mockAttemptReads("IN_PROGRESS");

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> service.result(studentId, attemptId));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }

    private void mockAttemptReads(String status) {
        Map<String, Object> attempt = attemptRow(status);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenAnswer(invocation -> {
            String sql = invocation.getArgument(0);
            if (sql.contains("status='IN_PROGRESS' FOR UPDATE OF a")) return List.of();
            if (sql.startsWith("SELECT a.id,a.exam_id,a.student_id")) return List.of(attempt);
            return List.of();
        });
    }

    private void mockOwnedAttemptRead(String status) {
        when(jdbc.queryForList(startsWith("SELECT a.id,a.exam_id,a.student_id"), any(Object[].class)))
                .thenReturn(List.of(attemptRow(status)));
    }

    private Map<String, Object> attemptRow(String status) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", attemptId);
        row.put("exam_id", examId);
        row.put("student_id", studentId);
        row.put("attempt_number", 1);
        row.put("status", status);
        row.put("started_at", now.minusMinutes(1));
        row.put("submitted_at", null);
        row.put("duration_minutes", 60);
        row.put("ends_at", null);
        row.put("negative_marking", new BigDecimal("0.25"));
        row.put("passing_marks", new BigDecimal("1.00"));
        return row;
    }

    private Map<String, Object> questionRow(String answer) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", questionId.toString());
        row.put("question_id", questionId.toString());
        row.put("prompt", "Choose A");
        row.put("question_type", "SINGLE_CHOICE");
        row.put("options", "[\"A\",\"B\"]");
        row.put("points", new BigDecimal("2.00"));
        row.put("answer", answer);
        row.put("correct_answer", "\"A\"");
        row.put("marked_for_review", false);
        return row;
    }

    private Map<String, Object> resultRow(String status, boolean showAnswers) {
        Map<String, Object> row = new HashMap<>();
        row.put("attemptId", attemptId.toString());
        row.put("status", status);
        row.put("score", new BigDecimal("2.00"));
        row.put("max_score", new BigDecimal("2.00"));
        row.put("maxScore", new BigDecimal("2.00"));
        row.put("percentage", new BigDecimal("100.00"));
        row.put("passed", true);
        row.put("graded_at", now);
        row.put("show_answers_after_submit", showAnswers);
        return row;
    }

    private Map<String, Object> exam(String id, OffsetDateTime startsAt, boolean completed) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("title", id);
        row.put("starts_at", startsAt);
        row.put("active_attempt", null);
        row.put("completed", completed);
        row.put("latest_attempt", null);
        row.put("attempt_count", 0L);
        return row;
    }
}
