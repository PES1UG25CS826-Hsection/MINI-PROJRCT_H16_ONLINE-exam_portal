package edu.pes.exam.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenServiceTest {
    private final TokenService tokens=new TokenService("test-secret-that-is-long-enough-for-hmac-sha256");

    @Test void verifiesIssuedUserAndRole() {
        UUID id=UUID.randomUUID();
        TokenService.Principal principal=tokens.verify(tokens.issue(id,"STUDENT"));
        assertEquals(id,principal.userId());
        assertEquals("STUDENT",principal.role());
    }

    @Test void rejectsTamperedAndMalformedTokens() {
        String token=tokens.issue(UUID.randomUUID(),"STUDENT");
        assertNull(tokens.verify(token+"x"));
        assertNull(tokens.verify("not-a-token"));
    }
}
