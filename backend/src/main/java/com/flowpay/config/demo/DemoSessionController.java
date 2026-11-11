package com.flowpay.config.demo;

import com.flowpay.company.CompanyUnavailableException;
import com.flowpay.company.CompanyView;
import com.flowpay.config.security.SessionIdentity;
import com.flowpay.identity.OperatorUnavailableException;
import com.flowpay.identity.OperatorView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class DemoSessionController {
    record UserResponse(UUID id, String email, Instant createdAt) {}
    record SessionResponse(UserResponse user, CompanyView company) {}

    private final DemoSessionService demos;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final SecurityContextRepository contexts;

    DemoSessionController(DemoSessionService demos, SessionAuthenticationStrategy sessionStrategy,
            SecurityContextRepository contexts) {
        this.demos = demos;
        this.sessionStrategy = sessionStrategy;
        this.contexts = contexts;
    }

    @PostMapping("/api/v1/demo/sessions")
    ResponseEntity<SessionResponse> enter(@AuthenticationPrincipal SessionIdentity current,
            HttpServletRequest request, HttpServletResponse response) {
        DemoSessionService.Session result;
        try {
            result = demos.reuseCurrent(current).orElse(null);
        } catch (CompanyUnavailableException | OperatorUnavailableException unavailable) {
            result = null;
        }
        if (result == null) result = demos.createFresh();

        var operator = result.user();
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                new SessionIdentity(operator.id(), operator.companyId()), null, List.of());
        sessionStrategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);

        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(session(operator, result.company()));
    }

    private SessionResponse session(OperatorView operator, CompanyView company) {
        return new SessionResponse(new UserResponse(operator.id(), operator.email(), operator.createdAt()), company);
    }
}
