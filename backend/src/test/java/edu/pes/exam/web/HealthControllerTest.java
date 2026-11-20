package edu.pes.exam.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HealthControllerTest {
    @Test
    void reportsServiceStatus() {
        assertEquals("UP", new HealthController().health().get("status"));
        assertEquals("online-exam-api", new HealthController().health().get("service"));
    }
}
