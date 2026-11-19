CREATE TABLE student_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500) NOT NULL DEFAULT '',
    created_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_student_groups_owner_name ON student_groups(created_by, lower(name));

CREATE TABLE group_members (
    group_id UUID NOT NULL REFERENCES student_groups(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    added_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (group_id, student_id)
);
CREATE INDEX ix_group_members_student ON group_members(student_id, group_id);

CREATE TABLE exam_groups (
    exam_id UUID NOT NULL REFERENCES exams(id) ON DELETE CASCADE,
    group_id UUID NOT NULL REFERENCES student_groups(id) ON DELETE CASCADE,
    assigned_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (exam_id, group_id)
);
CREATE INDEX ix_exam_groups_group ON exam_groups(group_id, exam_id);
