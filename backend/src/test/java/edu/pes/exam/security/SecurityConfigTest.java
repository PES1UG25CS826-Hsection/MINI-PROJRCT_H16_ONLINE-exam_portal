package edu.pes.exam.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import edu.pes.exam.web.HealthController;

@WebMvcTest(HealthController.class)
@Import({SecurityConfig.class, BearerTokenFilter.class})
class SecurityConfigTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private TokenService tokens;
    @MockitoBean private JdbcTemplate jdbc;

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void protectedPathsRequireAuthenticationButLoginIsPublic() throws Exception {
        mvc.perform(get("/api/private"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/login"))
                .andExpect(status().isNotFound());
    }
}
