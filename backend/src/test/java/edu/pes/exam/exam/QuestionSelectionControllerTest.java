package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pes.exam.security.PortalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class QuestionSelectionControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final QuestionBankController controller = new QuestionBankController(jdbc, new ObjectMapper());
    private final UUID teacherId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();
    private final UUID questionId = UUID.randomUUID();
    private final PortalPrincipal teacher = new PortalPrincipal(teacherId, "teacher@example.test", "TEACHER");

    @BeforeEach
    void allowOwnedDraftAndQuestion() {
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM exams"), eq(Integer.class), any(), any(), any()))
                .thenReturn(1);
        when(jdbc.queryForObject(startsWith("SELECT status FROM exams"), eq(String.class), eq(examId)))
                .thenReturn("DRAFT");
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM questions"), eq(Integer.class), eq(questionId), any(), any()))
                .thenReturn(1);
    }

    @Test
    void appendsSelectedQuestionWithOverrideAndPersistsOrder() {
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM exam_questions"), eq(Integer.class), eq(examId), eq(questionId)))
                .thenReturn(0);
        when(jdbc.queryForObject(startsWith("SELECT COALESCE(MAX(position)"), eq(Integer.class), eq(examId)))
                .thenReturn(2);

        Map<String, Object> result = controller.addToExam(examId, questionId,
                new QuestionBankController.LinkRequest(new BigDecimal("3.50")), teacher);

        assertEquals(2, result.get("position"));
        verify(jdbc).update(startsWith("INSERT INTO exam_questions(exam_id,question_id,position,points_override)"),
                eq(examId), eq(questionId), eq(2), eq(new BigDecimal("3.50")));
        verify(jdbc).update(startsWith("INSERT INTO audit_log"), eq(teacherId), eq("QUESTION_ASSIGN"), eq(questionId.toString()));
    }

    @Test
    void rejectsSelectingQuestionAlreadyLinkedToExam() {
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM exam_questions"), eq(Integer.class), eq(examId), eq(questionId)))
                .thenReturn(1);

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.addToExam(examId,
                questionId, new QuestionBankController.LinkRequest(BigDecimal.ONE), teacher));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void rejectsSelectingQuestionsForPublishedExam() {
        when(jdbc.queryForObject(startsWith("SELECT status FROM exams"), eq(String.class), eq(examId)))
                .thenReturn("PUBLISHED");

        ResponseStatusException error = assertThrows(ResponseStatusException.class, () -> controller.addToExam(examId,
                questionId, new QuestionBankController.LinkRequest(BigDecimal.ONE), teacher));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void removesSelectedQuestionOnlyFromDraftAndAuditsChange() {
        when(jdbc.update(startsWith("DELETE FROM exam_questions"), eq(examId), eq(questionId), eq(questionId), eq("TEACHER"), eq(teacherId))).thenReturn(1);

        Map<String, Object> result = controller.removeFromExam(examId, questionId, teacher);

        assertEquals(true, result.get("removed"));
        verify(jdbc).update(startsWith("DELETE FROM exam_questions"), eq(examId), eq(questionId), eq(questionId), eq("TEACHER"), eq(teacherId));
        verify(jdbc).update(startsWith("INSERT INTO audit_log"), eq(teacherId), eq("QUESTION_UNASSIGN"), eq(questionId.toString()));
    }

    @Test
    void rejectsRemovingQuestionWhenExamIsPublished() {
        when(jdbc.queryForObject(startsWith("SELECT status FROM exams"), eq(String.class), eq(examId)))
                .thenReturn("PUBLISHED");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.removeFromExam(examId, questionId, teacher));

        assertEquals(409, error.getStatusCode().value());
    }
}
