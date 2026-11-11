package com.flowpay.config.security;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.CompanyView;
import com.flowpay.identity.IdentityAccess;
import com.flowpay.identity.OperatorView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class AuthenticationController {
    private final IdentityAccess operators;
    private final CompanyAccess companies;
    private final SessionAuthenticationStrategy sessions;
    private final SecurityContextRepository contexts;
    private final AuthenticationManager authentications;

    AuthenticationController(IdentityAccess operators, CompanyAccess companies,
            SessionAuthenticationStrategy sessions, SecurityContextRepository contexts,
            AuthenticationManager authentications) {
        this.operators = operators;
        this.companies = companies;
        this.sessions = sessions;
        this.contexts = contexts;
        this.authentications = authentications;
    }

    record LoginRequest(String email, String password) {}
    record CsrfResponse(String token, String headerName, String parameterName) {}
    record UserResponse(UUID id, String email, Instant createdAt) {}
    record SessionResponse(UserResponse user, CompanyView company) {}

    @GetMapping("/api/v1/auth/csrf")
    CsrfResponse csrf(CsrfToken token) {
        return new CsrfResponse(token.getToken(), token.getHeaderName(), token.getParameterName());
    }

    @PostMapping("/api/v1/auth/login")
    SessionResponse login(@RequestBody LoginRequest body, HttpServletRequest request, HttpServletResponse response) {
        var authentication = authentications.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(body.email(), body.password()));
        var identity = (SessionIdentity) authentication.getPrincipal();
        OperatorView operator = operators.requireOperator(identity.userId(), identity.companyId());
        var result = session(operator);
        sessions.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return result;
    }

    @GetMapping("/api/v1/auth/me")
    SessionResponse me(@AuthenticationPrincipal SessionIdentity identity) {
        return session(operators.requireOperator(identity.userId(), identity.companyId()));
    }

    @GetMapping("/api/v1/company")
    CompanyView company(@AuthenticationPrincipal SessionIdentity identity) {
        return companies.requireActive(identity.companyId());
    }

    @PostMapping("/api/v1/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, authentication);
    }

    private SessionResponse session(OperatorView operator) {
        return new SessionResponse(new UserResponse(operator.id(), operator.email(), operator.createdAt()),
                companies.requireActive(operator.companyId()));
    }
}
