package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ExamAndQuestionValidationTest {
    private final ExamController exams = new ExamController(null, null, null);
    private final QuestionBankController questions = new QuestionBankController(null, null);

    @Test
    void rejectsExamWhoseEndDoesNotFollowItsStart() {
        var request = examRequest(Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-20T10:00:00Z"),
                new BigDecimal("10"), new BigDecimal("5"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> exams.createExam(request, null));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsPassingMarksAboveExamTotal() {
        var request = examRequest(Instant.parse("2026-11-20T10:00:00Z"), Instant.parse("2026-11-20T11:00:00Z"),
                new BigDecimal("10"), new BigDecimal("11"));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> exams.createExam(request, null));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsUnsupportedQuestionTypeBeforePersistence() {
        var request = new QuestionBankController.QuestionRequest("Prompt", "ESSAY", List.of("A", "B"),
                List.of("A"), BigDecimal.ONE);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> questions.create(request, null));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void rejectsDuplicateQuestionOptions() {
        var request = new QuestionBankController.QuestionRequest("Prompt", "MCQ", List.of("A", "A"),
                List.of("A"), BigDecimal.ONE);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> questions.create(request, null));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void requiresExactTrueFalseOptionLabels() {
        var request = new QuestionBankController.QuestionRequest("Prompt", "TRUE_FALSE", List.of("Yes", "No"),
                List.of("Yes"), BigDecimal.ONE);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> questions.create(request, null));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    private ExamController.ExamRequest examRequest(Instant start, Instant end, BigDecimal total, BigDecimal passing) {
        return new ExamController.ExamRequest("Exam", "Subject", 30, start, end, total, passing,
                0, false, 1, false, false);
    }
}
