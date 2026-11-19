CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(160) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN')),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_email_lowercase CHECK (email = lower(email))
);

CREATE TABLE questions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    prompt TEXT NOT NULL,
    question_type VARCHAR(20) NOT NULL DEFAULT 'MCQ' CHECK (question_type IN ('MCQ', 'TRUE_FALSE', 'SHORT_ANSWER')),
    options JSONB NOT NULL DEFAULT '[]'::jsonb,
    correct_answer JSONB NOT NULL,
    points NUMERIC(8,2) NOT NULL DEFAULT 1 CHECK (points > 0),
    explanation TEXT,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_questions_options_array CHECK (jsonb_typeof(options) = 'array')
);
CREATE INDEX ix_questions_author_id ON questions(author_id);

CREATE TABLE exams (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes BETWEEN 1 AND 1440),
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ,
    randomize_questions BOOLEAN NOT NULL DEFAULT FALSE,
    randomize_options BOOLEAN NOT NULL DEFAULT FALSE,
    max_attempts INTEGER NOT NULL DEFAULT 1 CHECK (max_attempts > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_exams_window CHECK (starts_at IS NULL OR ends_at IS NULL OR starts_at < ends_at)
);
CREATE INDEX ix_exams_status_window ON exams(status, starts_at, ends_at);
CREATE INDEX ix_exams_created_by ON exams(created_by);

CREATE TABLE exam_questions (
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    position INTEGER NOT NULL CHECK (position >= 0),
    points_override NUMERIC(8,2) CHECK (points_override IS NULL OR points_override > 0),
    PRIMARY KEY (exam_id, question_id),
    CONSTRAINT uq_exam_questions_position UNIQUE (exam_id, position)
);

CREATE TABLE attempts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE RESTRICT,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'TIMED_OUT', 'CANCELLED')),
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    submitted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_attempts_student_number UNIQUE (exam_id, student_id, attempt_number),
    CONSTRAINT ck_attempts_submission CHECK (submitted_at IS NULL OR submitted_at >= started_at)
);
CREATE INDEX ix_attempts_student_started ON attempts(student_id, started_at DESC);
CREATE INDEX ix_attempts_exam_status ON attempts(exam_id, status);

CREATE TABLE attempt_answers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL REFERENCES attempts(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES questions(id) ON DELETE RESTRICT,
    answer JSONB,
    is_correct BOOLEAN,
    awarded_points NUMERIC(8,2) CHECK (awarded_points IS NULL OR awarded_points >= 0),
    saved_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_attempt_answers_question UNIQUE (attempt_id, question_id)
);

CREATE TABLE results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    attempt_id UUID NOT NULL UNIQUE REFERENCES attempts(id) ON DELETE RESTRICT,
    score NUMERIC(10,2) NOT NULL CHECK (score >= 0),
    max_score NUMERIC(10,2) NOT NULL CHECK (max_score > 0 AND score <= max_score),
    percentage NUMERIC(5,2) GENERATED ALWAYS AS (round((score / max_score) * 100, 2)) STORED,
    passed BOOLEAN NOT NULL,
    graded_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    graded_by UUID REFERENCES users(id) ON DELETE SET NULL
);

CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id VARCHAR(100),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    ip_address INET,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_audit_details_object CHECK (jsonb_typeof(details) = 'object')
);
CREATE INDEX ix_audit_log_created_at ON audit_log(created_at DESC);
CREATE INDEX ix_audit_log_actor_created ON audit_log(actor_id, created_at DESC);
CREATE INDEX ix_audit_log_entity ON audit_log(entity_type, entity_id);
