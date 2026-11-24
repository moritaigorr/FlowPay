package com.flowpay.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;
import java.util.UUID;
import com.flowpay.identity.api.TenantAccess;
import com.flowpay.identity.application.AuthenticationRateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class IdentityController {
    private final JdbcTemplate jdbc;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contexts;
    private final org.springframework.security.crypto.password.PasswordEncoder passwords;
    private final TenantAccess tenantAccess;
    private final boolean registrationEnabled;
    private final AuthenticationRateLimiter rateLimiter;

    public IdentityController(JdbcTemplate jdbc, AuthenticationManager authenticationManager,
                              SecurityContextRepository contexts,
                              org.springframework.security.crypto.password.PasswordEncoder passwords,
                              TenantAccess tenantAccess,
                              AuthenticationRateLimiter rateLimiter,
                              @Value("${flowpay.identity.registration.enabled:false}") boolean registrationEnabled) {
        this.jdbc = jdbc;
        this.authenticationManager = authenticationManager;
        this.contexts = contexts;
        this.passwords = passwords;
        this.tenantAccess = tenantAccess;
        this.rateLimiter = rateLimiter;
        this.registrationEnabled = registrationEnabled;
    }

    @GetMapping("/csrf")
    CsrfResponse csrf(CsrfToken token) { return new CsrfResponse(token.getToken()); }

    @GetMapping("/config")
    AuthConfigResponse config() { return new AuthConfigResponse(registrationEnabled); }

    @GetMapping("/session")
    SessionResponse session(@AuthenticationPrincipal UserDetails user) {
        var tenants = tenantAccess.listTenants(user.getUsername());
        var defaultTenant = tenants.isEmpty() ? null : tenants.getFirst();
        return new SessionResponse(user.getUsername(), defaultTenant == null ? null : defaultTenant.id(),
                defaultTenant == null ? null : defaultTenant.name(), defaultTenant == null ? null : defaultTenant.role(), tenants);
    }

    @PostMapping("/register")
    @Transactional
    RegistrationResponse register(@Valid @RequestBody RegistrationRequest input,
                                  HttpServletRequest request, HttpServletResponse response) {
        if (!registrationEnabled) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cadastro indisponível neste ambiente.");
        }
        var registrationLimit = rateLimiter.reserve("registration", clientAddress(request), 5, java.time.Duration.ofHours(1));
        if (!registrationLimit.allowed()) {
            response.setHeader("Retry-After", Integer.toString(registrationLimit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Limite de tentativas atingido. Tente novamente mais tarde.");
        }
        var email = input.email().trim().toLowerCase(Locale.ROOT);
        var password = input.password();
        if (password.length() < 12 || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A senha deve ter no mínimo 12 caracteres e até 72 bytes em UTF-8.");
        }

        var userId = UUID.randomUUID();
        var tenantId = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO app_user(id, email, password_hash) VALUES (?, ?, ?)", userId, email, passwords.encode(password));
            jdbc.update("INSERT INTO tenant(id, name) VALUES (?, ?)", tenantId, input.companyName().trim());
            jdbc.update("INSERT INTO membership(tenant_id, user_id, role) VALUES (?, ?, 'OWNER')", tenantId, userId);
        } catch (DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não foi possível criar a conta com esses dados.");
        }

        var authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(email, password));
        request.getSession(true);
        request.changeSessionId();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return new RegistrationResponse(userId, tenantId, input.companyName().trim());
    }

    @PostMapping("/login")
    LoginResponse login(@Valid @RequestBody LoginRequest input,
                        HttpServletRequest request, HttpServletResponse response) {
        var email = input.email().trim().toLowerCase(Locale.ROOT);
        var rateLimitIdentity = clientAddress(request) + "\u0000" + email;
        var loginLimit = rateLimiter.reserve("login", rateLimitIdentity, 5, java.time.Duration.ofMinutes(15));
        if (!loginLimit.allowed()) {
            response.setHeader("Retry-After", Integer.toString(loginLimit.retryAfterSeconds()));
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "E-mail ou senha inválidos.");
        }
        try {
            var authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, input.password()));
            rateLimiter.clear("login", rateLimitIdentity);
            request.getSession(true);
            request.changeSessionId();
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
            contexts.saveContext(context, request, response);
            return new LoginResponse(email);
        } catch (org.springframework.security.core.AuthenticationException rejected) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos.");
        }
    }

    private static String clientAddress(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? "unknown" : address;
    }

    record RegistrationRequest(@NotBlank @Email @Size(max = 254) String email,
                               @NotBlank @Size(max = 120) String companyName,
                               @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "RegistrationRequest[REDACTED]"; }
    }
    record RegistrationResponse(UUID userId, UUID tenantId, String companyName) {
        @Override public String toString() { return "RegistrationResponse[REDACTED]"; }
    }
    record LoginRequest(@NotBlank @Email @Size(max = 254) String email, @NotBlank @Size(max = 72) String password) {
        @Override public String toString() { return "LoginRequest[REDACTED]"; }
    }
    record LoginResponse(String email) {
        @Override public String toString() { return "LoginResponse[REDACTED]"; }
    }
    record SessionResponse(String email, UUID tenantId, String companyName, String role, java.util.List<TenantAccess.CurrentTenant> tenants) {
        @Override public String toString() { return "SessionResponse[REDACTED]"; }
    }
    record CsrfResponse(String token) {
        @Override public String toString() { return "CsrfResponse[REDACTED]"; }
    }
    record AuthConfigResponse(boolean registrationEnabled) {}
}
