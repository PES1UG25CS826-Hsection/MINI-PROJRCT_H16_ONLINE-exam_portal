package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.pes.exam.security.PortalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

class QuestionBankControllerTest {
    private JdbcTemplate jdbc;
    private QuestionBankController controller;
    private final UUID teacherId = UUID.randomUUID();
    private final PortalPrincipal teacher = new PortalPrincipal(teacherId, "teacher@example.test", "TEACHER");

    @BeforeEach
    void setUp() {
        jdbc = org.mockito.Mockito.mock(JdbcTemplate.class);
        controller = new QuestionBankController(jdbc, new ObjectMapper());
    }

    @Test
    void createsSingleChoiceMultiSelectAndTrueFalseQuestions() {
        var single = controller.create(question("MCQ", List.of("A", "B"), List.of("A")), teacher);
        var multiple = controller.create(question("MULTI_SELECT", List.of("A", "B", "C"), List.of("A", "C")), teacher);
        var booleanQuestion = controller.create(question("TRUE_FALSE", List.of("True", "False"), List.of("True")), teacher);

        assertEquals("MCQ", single.get("type"));
        assertEquals(List.of("A", "C"), multiple.get("correctAnswers"));
        assertEquals("TRUE_FALSE", booleanQuestion.get("type"));
        assertEquals(6, org.mockito.Mockito.mockingDetails(jdbc).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("update")).count());
        verify(jdbc, times(6)).update(anyString(), any(Object[].class));
    }

    @Test
    void listsOnlyQuestionsOwnedByTheTeacher() {
        doReturn(List.of()).when(jdbc).query(anyString(), any(RowMapper.class), eq("TEACHER"), eq(teacherId));

        assertTrue(controller.list(teacher).isEmpty());

        verify(jdbc).query(startsWith("SELECT id,prompt,question_type"), any(RowMapper.class), eq("TEACHER"), eq(teacherId));
    }

    @Test
    void updatesOwnedQuestionWhenItIsNotInUse() {
        doReturn(1, 0).when(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
        UUID questionId = UUID.randomUUID();

        var response = controller.update(questionId, question("MCQ", List.of("A", "B"), List.of("B")), teacher);

        assertEquals(questionId, response.get("id"));
        assertEquals(true, response.get("updated"));
        verify(jdbc).update(startsWith("UPDATE questions SET"), any(Object[].class));
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void blocksUpdatingQuestionUsedByPublishedExam() {
        doReturn(1, 1).when(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.update(UUID.randomUUID(), question("MCQ", List.of("A", "B"), List.of("B")), teacher));

        assertEquals("A question used by a published exam or attempt cannot be changed.", error.getReason());
        verify(jdbc, times(0)).update(anyString(), any(Object[].class));
    }

    @Test
    void preventsUpdatingAnotherTeachersQuestion() {
        doReturn(0).when(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.update(UUID.randomUUID(), question("MCQ", List.of("A", "B"), List.of("B")), teacher));

        assertEquals("Question not found.", error.getReason());
        verify(jdbc, times(0)).update(anyString(), any(Object[].class));
    }

    @Test
    void deletesOwnedQuestionWhenUnassigned() {
        doReturn(1, 0).when(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));
        UUID questionId = UUID.randomUUID();

        var response = controller.delete(questionId, teacher);

        assertEquals(questionId, response.get("id"));
        assertEquals(true, response.get("deleted"));
        verify(jdbc).update(startsWith("DELETE FROM questions"), any(Object[].class));
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void blocksDeletingQuestionStillAssignedToAnExam() {
        doReturn(1, 1).when(jdbc).queryForObject(anyString(), eq(Integer.class), any(Object[].class));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.delete(UUID.randomUUID(), teacher));

        assertEquals("Remove this question from its draft exams before deleting it.", error.getReason());
        verify(jdbc, times(0)).update(anyString(), any(Object[].class));
    }

    @Test
    void rejectsInvalidTypeAndDuplicateOptionsBeforeDatabaseAccess() {
        ResponseStatusException invalidType = assertThrows(ResponseStatusException.class,
                () -> controller.create(question("ESSAY", List.of("A", "B"), List.of("A")), teacher));
        ResponseStatusException duplicateOptions = assertThrows(ResponseStatusException.class,
                () -> controller.create(question("MCQ", List.of("A", "A"), List.of("A")), teacher));

        assertEquals("Question type must be MCQ, MULTI_SELECT, or TRUE_FALSE.", invalidType.getReason());
        assertEquals("Options and correct answers must be unique, and every correct answer must be an option.", duplicateOptions.getReason());
        verifyNoInteractions(jdbc);
    }

    @Test
    void enforcesOptionAndSingleAnswerCounts() {
        ResponseStatusException tooFew = assertThrows(ResponseStatusException.class,
                () -> controller.create(question("MCQ", List.of("A"), List.of("A")), teacher));
        ResponseStatusException tooMany = assertThrows(ResponseStatusException.class,
                () -> controller.create(question("MCQ", List.of("A", "B", "C", "D", "E", "F", "G"), List.of("A")), teacher));
        ResponseStatusException multipleCorrect = assertThrows(ResponseStatusException.class,
                () -> controller.create(question("MCQ", List.of("A", "B"), List.of("A", "B")), teacher));

        assertEquals("Multiple-choice questions need 2 to 6 options.", tooFew.getReason());
        assertEquals("Multiple-choice questions need 2 to 6 options.", tooMany.getReason());
        assertEquals("Single-choice and True/False questions need exactly one correct answer.", multipleCorrect.getReason());
        verifyNoInteractions(jdbc);
    }

    private QuestionBankController.QuestionRequest question(String type, List<String> options, List<String> correct) {
        return new QuestionBankController.QuestionRequest("Sample question", type, options, correct, new BigDecimal("2.00"));
    }
}
