package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AttemptDeadlineTest {
    private static final Instant START = Instant.parse("2026-11-20T10:00:00Z");

    @Test
    void scheduledExamEndShortensTheAttemptDeadline() {
        Instant scheduledEnd = START.plusSeconds(600);
        assertEquals(scheduledEnd, AttemptDeadline.at(START, 30, scheduledEnd));
    }

    @Test
    void durationSetsTheAttemptDeadlineWhenItEndsFirst() {
        Instant scheduledEnd = START.plusSeconds(3600);
        assertEquals(START.plusSeconds(1800), AttemptDeadline.at(START, 30, scheduledEnd));
    }

    @Test
    void acceptsAnswersThroughTheTenSecondGraceWindowOnly() {
        Instant deadline = START.plusSeconds(60);
        assertFalse(AttemptDeadline.answerWindowClosed(deadline.plusSeconds(10), deadline));
        assertTrue(AttemptDeadline.answerWindowClosed(deadline.plusSeconds(11), deadline));
    }
}
