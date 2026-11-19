package edu.pes.exam.exam;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import edu.pes.exam.security.PortalPrincipal;

class GroupControllerTest {
    private JdbcTemplate jdbc;
    private GroupController controller;
    private final PortalPrincipal teacher = new PortalPrincipal(UUID.randomUUID(), "teacher@example.test", "TEACHER");
    private final UUID groupId = UUID.randomUUID();
    private final UUID studentId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = mock(JdbcTemplate.class);
        controller = new GroupController(jdbc);
    }

    @Test
    void rejectsBlankAndDuplicateGroupNames() {
        var blank = assertThrows(ResponseStatusException.class, () -> controller.create(new GroupController.GroupRequest("  ", ""), teacher));
        assertEquals(400, blank.getStatusCode().value());
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        var duplicate = assertThrows(ResponseStatusException.class, () -> controller.create(new GroupController.GroupRequest("Section A", ""), teacher));
        assertEquals(409, duplicate.getStatusCode().value());
    }

    @Test
    void trimsAndCreatesGroupAndAuditsIt() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);
        var result = controller.create(new GroupController.GroupRequest("  Section A  ", "  morning class  "), teacher);
        assertEquals("Section A", result.get("name"));
        assertEquals("morning class", result.get("description"));
        assertEquals(0, result.get("memberCount"));
        verify(jdbc, times(2)).update(anyString(), any(Object[].class));
    }

    @Test
    void ownerCheckProtectsMembersAndMissingStudentIsNotAdded() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0, 1);
        var hidden = assertThrows(ResponseStatusException.class, () -> controller.members(groupId, teacher));
        assertEquals(404, hidden.getStatusCode().value());
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        var missing = assertThrows(ResponseStatusException.class, () -> controller.addMember(groupId,
                new GroupController.MemberRequest("student@example.test"), teacher));
        assertEquals(404, missing.getStatusCode().value());
        verify(jdbc, never()).update(contains("INSERT INTO group_members"), any(Object[].class));
    }

    @Test
    void missingMembershipCannotBeRemovedAndGroupListingUsesOwnerScope() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.update(startsWith("DELETE FROM group_members"), any(Object[].class))).thenReturn(0);
        var missing = assertThrows(ResponseStatusException.class, () -> controller.removeMember(groupId, studentId, teacher));
        assertEquals(404, missing.getStatusCode().value());
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        assertTrue(controller.list(teacher).isEmpty());
    }
    @Test
    void addsExistingStudentAndRemovesMembership() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of(studentId));
        when(jdbc.update(startsWith("DELETE FROM group_members"), any(Object[].class))).thenReturn(1);
        var added = controller.addMember(groupId, new GroupController.MemberRequest(" Student@Example.Test "), teacher);
        assertEquals(studentId, added.get("studentId"));
        assertEquals("student@example.test", added.get("email"));
        var removed = controller.removeMember(groupId, studentId, teacher);
        assertEquals(true, removed.get("removed"));
        verify(jdbc, times(4)).update(anyString(), any(Object[].class));
    }}