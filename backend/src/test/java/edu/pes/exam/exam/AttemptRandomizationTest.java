package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pes.exam.security.PortalPrincipal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AttemptRandomizationTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExamController controller = new ExamController(jdbc, new ObjectMapper(), new ExamScoringService());
    private final UUID studentId = UUID.fromString("e57e2293-fd93-48b2-bf0a-bc3b9633f092");
    private final UUID examId = UUID.fromString("2f1c50a0-e919-4144-8e28-540dc266ea10");
    private final UUID attemptId = UUID.fromString("9912ffab-4660-4daf-a4cd-3ceaa1d3178c");
    private final PortalPrincipal student = new PortalPrincipal(studentId, "student@example.test", "STUDENT");
    private final List<UUID> questionIds = List.of(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"));

    @Test
    void enabledRandomizationIsStableForSameAttemptAndHidesAnswerKey() throws Exception {
        stubAttempt(true, true);

        Map<String, Object> first = controller.getAttempt(attemptId, student);
        Map<String, Object> second = controller.getAttempt(attemptId, student);

        assertEquals(questionOrder(first), questionOrder(second));
        assertEquals(optionOrder(first), optionOrder(second));
        assertNotEquals(questionIds, questionOrder(first));
        List<Map<String, Object>> questions = questions(first);
        assertTrue(questions.stream().anyMatch(question -> !List.of("A", "B", "C").equals(question.get("options"))));
        assertEquals(java.util.Set.of("B"), questions.get(0).get("answer"));
        assertTrue(((List<?>) questions.get(0).get("options")).contains("B"));
        assertFalse(questions.get(0).containsKey("correctAnswers"));
    }

    @Test
    void disabledRandomizationPreservesStoredQuestionAndOptionOrder() throws Exception {
        stubAttempt(false, false);

        Map<String, Object> attempt = controller.getAttempt(attemptId, student);

        assertEquals(questionIds, questionOrder(attempt));
        assertEquals(List.of("A", "B", "C"), questions(attempt).get(0).get("options"));
    }

    private void stubAttempt(boolean randomizeQuestions, boolean randomizeOptions) throws Exception {
        ResultSet attemptRow = mock(ResultSet.class);
        when(attemptRow.getObject("exam_id", UUID.class)).thenReturn(examId);
        when(attemptRow.getString("status")).thenReturn("IN_PROGRESS");
        when(attemptRow.getTimestamp("started_at")).thenReturn(Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")));
        when(attemptRow.getTimestamp("submitted_at")).thenReturn(null);
        when(attemptRow.getString("title")).thenReturn("Midterm");
        when(attemptRow.getInt("duration_minutes")).thenReturn(60);
        when(attemptRow.getTimestamp("ends_at")).thenReturn(Timestamp.from(Instant.parse("2026-01-02T00:00:00Z")));
        when(attemptRow.getBoolean("show_answers_after_submit")).thenReturn(false);
        when(attemptRow.getBoolean("randomize_questions")).thenReturn(randomizeQuestions);
        when(attemptRow.getBoolean("randomize_options")).thenReturn(randomizeOptions);
        when(jdbc.query(startsWith("SELECT a.exam_id"), any(RowMapper.class), eq(attemptId), eq(studentId)))
                .thenAnswer(invocation -> List.of(((RowMapper<?>) invocation.getArgument(1)).mapRow(attemptRow, 0)));

        when(jdbc.query(startsWith("SELECT q.id,q.prompt"), any(RowMapper.class), eq(attemptId), eq(examId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    List<Object> rows = new ArrayList<>();
                    for (int i = 0; i < questionIds.size(); i++) {
                        ResultSet question = mock(ResultSet.class);
                        when(question.getObject("id", UUID.class)).thenReturn(questionIds.get(i));
                        when(question.getString("prompt")).thenReturn("Question " + (i + 1));
                        when(question.getString("question_type")).thenReturn("MCQ");
                        when(question.getString("options")).thenReturn("[\"A\",\"B\",\"C\"]");
                        when(question.getBigDecimal("points")).thenReturn(BigDecimal.ONE);
                        when(question.getString("answer")).thenReturn("[\"B\"]");
                        when(question.getBoolean("marked")).thenReturn(false);
                        rows.add(mapper.mapRow(question, i));
                    }
                    return rows;
                });
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> questions(Map<String, Object> attempt) {
        return (List<Map<String, Object>>) attempt.get("questions");
    }

    private List<UUID> questionOrder(Map<String, Object> attempt) {
        return questions(attempt).stream().map(question -> (UUID) question.get("id")).toList();
    }

    @SuppressWarnings("unchecked")
    private List<String> optionOrder(Map<String, Object> attempt) {
        return questions(attempt).stream().flatMap(question -> ((List<String>) question.get("options")).stream()).toList();
    }
}
