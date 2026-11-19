package edu.pes.exam.security;

import java.util.UUID;

public record PortalPrincipal(UUID id, String email, String role) { }
