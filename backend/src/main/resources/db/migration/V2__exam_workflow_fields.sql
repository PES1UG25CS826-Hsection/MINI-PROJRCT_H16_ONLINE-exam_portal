ALTER TABLE exams ADD COLUMN subject VARCHAR(160) NOT NULL DEFAULT 'General';
ALTER TABLE exams ADD COLUMN total_marks NUMERIC(10,2) NOT NULL DEFAULT 1 CHECK (total_marks > 0);
ALTER TABLE exams ADD COLUMN passing_marks NUMERIC(10,2) NOT NULL DEFAULT 0 CHECK (passing_marks >= 0 AND passing_marks <= total_marks);
ALTER TABLE exams ADD COLUMN negative_mark_percent INTEGER NOT NULL DEFAULT 0 CHECK (negative_mark_percent BETWEEN 0 AND 100);
ALTER TABLE exams ADD COLUMN show_answers_after_submit BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE attempt_answers ADD COLUMN marked_for_review BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE questions DROP CONSTRAINT IF EXISTS questions_question_type_check;
ALTER TABLE questions ADD CONSTRAINT ck_questions_type CHECK (question_type IN ('MCQ', 'MULTI_SELECT', 'TRUE_FALSE'));

CREATE INDEX ix_results_graded_at ON results(graded_at DESC);
