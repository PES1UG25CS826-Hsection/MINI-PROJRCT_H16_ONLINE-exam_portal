package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ExamScoringServiceTest {
    private final ExamScoringService scoring = new ExamScoringService();

    @Test
    void awardsFullPointsForCorrectSingleAnswer() {
        assertEquals(new BigDecimal("2.50"), scoring.score(new BigDecimal("2.5"), Set.of("B"), Set.of("B"), 25));
    }

    @Test
    void appliesConfiguredNegativeMarkingForWrongAnswer() {
        assertEquals(new BigDecimal("-0.50"), scoring.score(new BigDecimal("2.0"), Set.of("B"), Set.of("A"), 25));
    }

    @Test
    void zeroNegativeMarkingDoesNotDeductForWrongAnswer() {
        assertEquals(new BigDecimal("0.00"), scoring.score(new BigDecimal("2.0"), Set.of("B"), Set.of("A"), 0));
    }

    @Test
    void fullNegativeMarkingDeductsAllQuestionPoints() {
        assertEquals(new BigDecimal("-2.00"), scoring.score(new BigDecimal("2.0"), Set.of("B"), Set.of("A"), 100));
    }

    @Test
    void roundsNegativePenaltyToTwoDecimalPlaces() {
        assertEquals(new BigDecimal("-0.33"), scoring.score(new BigDecimal("1.0"), Set.of("B"), Set.of("A"), 33));
    }

    @Test
    void treatsUnansweredQuestionAsZero() {
        assertEquals(new BigDecimal("0.00"), scoring.score(BigDecimal.ONE, Set.of("B"), Set.of(), 50));
    }

    @Test
    void comparesMultiSelectAnswersWithoutOrderDependence() {
        assertEquals(new BigDecimal("3.00"), scoring.score(new BigDecimal("3"), Set.of("A", "C"), Set.of("C", "A"), 0));
    }

    @Test
    void clampsFinalExamScoreAtZero() {
        assertEquals(new BigDecimal("0.00"), scoring.total(List.of(new BigDecimal("-0.50"), new BigDecimal("0.00"))));
    }
}
