ALTER TABLE exams
    ADD COLUMN passing_marks NUMERIC(8,2) NOT NULL DEFAULT 0 CHECK (passing_marks >= 0),
    ADD COLUMN negative_marking NUMERIC(5,4) NOT NULL DEFAULT 0 CHECK (negative_marking BETWEEN 0 AND 1),
    ADD COLUMN show_question_review BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE attempt_answers
    ADD COLUMN is_marked_for_review BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE exam_assignments (
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (exam_id, student_id)
);
CREATE INDEX ix_exam_assignments_student ON exam_assignments(student_id, exam_id);
CREATE INDEX ix_attempts_in_progress_started ON attempts(started_at) WHERE status = 'IN_PROGRESS';
CREATE INDEX ix_attempt_answers_attempt ON attempt_answers(attempt_id, question_id);
