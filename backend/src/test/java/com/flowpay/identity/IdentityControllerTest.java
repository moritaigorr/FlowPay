package com.flowpay.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.flowpay.identity.api.TenantAccess;
import com.flowpay.identity.application.AuthenticationRateLimiter;
import com.flowpay.identity.application.ClientAddressResolver;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

class IdentityControllerTest {
    @Test
    void registrationIsUnavailableWhenTheEnvironmentDoesNotEnableIt() {
        var jdbc = mock(JdbcTemplate.class);
        var authenticationManager = mock(AuthenticationManager.class);
        var contexts = mock(SecurityContextRepository.class);
        var passwords = mock(PasswordEncoder.class);
        var tenantAccess = mock(TenantAccess.class);
        var rateLimiter = mock(AuthenticationRateLimiter.class);
        var clientAddressResolver = mock(ClientAddressResolver.class);
        var controller = new IdentityController(jdbc, authenticationManager, contexts, passwords, tenantAccess, rateLimiter, clientAddressResolver, false);

        assertThat(controller.config().registrationEnabled()).isFalse();
        assertThatThrownBy(() -> controller.register(
                new IdentityController.RegistrationRequest("owner@example.test", "Empresa teste", "Long-password-123"),
                null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getStatusCode()).isEqualTo(NOT_FOUND));
        verifyNoInteractions(jdbc, authenticationManager, contexts, passwords, tenantAccess, rateLimiter);
    }
}
