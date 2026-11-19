package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.UUID;

import edu.pes.exam.security.PortalPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class ExamGroupControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ExamGroupController controller = new ExamGroupController(jdbc);
    private final UUID teacherId = UUID.randomUUID();
    private final UUID examId = UUID.randomUUID();
    private final UUID groupId = UUID.randomUUID();
    private final PortalPrincipal teacher = new PortalPrincipal(teacherId, "teacher@example.test", "TEACHER");

    @BeforeEach
    void allowOwnedDraftAndGroup() {
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM exams"), eq(Integer.class), eq(examId), any(), any()))
                .thenReturn(1);
        when(jdbc.queryForObject(startsWith("SELECT status FROM exams"), eq(String.class), eq(examId)))
                .thenReturn("DRAFT");
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM student_groups"), eq(Integer.class), eq(groupId), any(), any()))
                .thenReturn(1);
    }

    @Test
    void assignsGroupToOwnedDraftAndPersistsMapping() {
        Map<String, Object> result = controller.assign(examId, groupId, teacher);

        assertEquals(true, result.get("assigned"));
        verify(jdbc).update(startsWith("INSERT INTO exam_groups"), eq(examId), eq(groupId));
        verify(jdbc).update(startsWith("INSERT INTO audit_log"), eq(teacherId), eq("EXAM_GROUP_ASSIGN"), eq(examId.toString()), eq(groupId.toString()));
    }

    @Test
    void rejectsAssignmentAfterExamIsPublished() {
        when(jdbc.queryForObject(startsWith("SELECT status FROM exams"), eq(String.class), eq(examId)))
                .thenReturn("PUBLISHED");

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.assign(examId, groupId, teacher));

        assertEquals(409, error.getStatusCode().value());
    }

    @Test
    void rejectsGroupNotOwnedByTeacherOrAdmin() {
        when(jdbc.queryForObject(startsWith("SELECT COUNT(*) FROM student_groups"), eq(Integer.class), eq(groupId), any(), any()))
                .thenReturn(0);

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> controller.assign(examId, groupId, teacher));

        assertEquals(404, error.getStatusCode().value());
    }

    @Test
    void unassignsGroupFromDraft() {
        when(jdbc.update(startsWith("DELETE FROM exam_groups"), eq(examId), eq(groupId))).thenReturn(1);

        Map<String, Object> result = controller.unassign(examId, groupId, teacher);

        assertEquals(false, result.get("assigned"));
        verify(jdbc).update(startsWith("DELETE FROM exam_groups"), eq(examId), eq(groupId));
        verify(jdbc).update(startsWith("INSERT INTO audit_log"), eq(teacherId), eq("EXAM_GROUP_UNASSIGN"), eq(examId.toString()), eq(groupId.toString()));
    }
}
