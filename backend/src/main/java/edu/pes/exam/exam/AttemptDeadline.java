package edu.pes.exam.exam;

import java.time.Instant;
import java.util.Objects;

/** Server-authoritative deadline calculations shared by attempts and answer saves. */
public final class AttemptDeadline {
    private static final long ANSWER_GRACE_SECONDS = 10;

    private AttemptDeadline() { }

    public static Instant at(Instant startedAt, int durationMinutes, Instant scheduledEnd) {
        Objects.requireNonNull(startedAt, "startedAt");
        Objects.requireNonNull(scheduledEnd, "scheduledEnd");
        Instant durationEnd = startedAt.plusSeconds(Math.multiplyExact((long) durationMinutes, 60L));
        return scheduledEnd.isBefore(durationEnd) ? scheduledEnd : durationEnd;
    }

    public static boolean answerWindowClosed(Instant serverNow, Instant deadline) {
        return serverNow.isAfter(deadline.plusSeconds(ANSWER_GRACE_SECONDS));
    }
}
