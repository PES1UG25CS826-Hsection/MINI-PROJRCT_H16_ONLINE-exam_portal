package edu.pes.exam.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnswerGraderTest {
    private final AnswerGrader grader=new AnswerGrader(new ObjectMapper());

    @Test void scoresCorrectIncorrectAndUnansweredWithConfiguredPenalty() {
        var grade=grader.grade(List.of(
                q("2","\"A\"","\"a\""),
                q("2","\"wrong\"","\"right\""),
                q("1",null,"\"true\"")),new BigDecimal("0.25"),new BigDecimal("1.5"));
        assertEquals(new BigDecimal("1.50"),grade.score());
        assertEquals(new BigDecimal("5.00"),grade.maxScore());
        assertEquals(new BigDecimal("30.00"),grade.percentage());
        assertTrue(grade.passed());
    }

    @Test void scoreNeverFallsBelowZeroAndDoesNotPass() {
        var grade=grader.grade(List.of(q("1","\"x\"","\"a\""),q("1","\"x\"","\"b\"")),new BigDecimal("1"),new BigDecimal("0.01"));
        assertEquals(new BigDecimal("0.00"),grade.score());
        assertFalse(grade.passed());
    }

    @Test void comparesStructuredAnswersAndRejectsEmptyExam() {
        assertTrue(grader.isCorrect("{\"choice\":2}","{\"choice\":2}"));
        assertThrows(IllegalArgumentException.class,()->grader.grade(List.of(),BigDecimal.ZERO,BigDecimal.ZERO));
    }

    private static AnswerGrader.QuestionSubmission q(String points,String answer,String correct) {
        return new AnswerGrader.QuestionSubmission(new BigDecimal(points),answer,correct);
    }
}
