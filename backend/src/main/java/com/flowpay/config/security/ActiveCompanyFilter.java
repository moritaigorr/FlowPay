package com.flowpay.config.security;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.CompanyUnavailableException;
import com.flowpay.config.web.ApiProblems;
import com.flowpay.identity.IdentityAccess;
import com.flowpay.identity.OperatorUnavailableException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class ActiveCompanyFilter extends OncePerRequestFilter {
    private final CompanyAccess companies;
    private final IdentityAccess operators;
    private final ApiProblems problems;

    ActiveCompanyFilter(CompanyAccess companies, IdentityAccess operators, ApiProblems problems) {
        this.companies = companies;
        this.operators = operators;
        this.problems = problems;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/") || path.equals("/api/v1/auth/login")
                || path.equals("/api/v1/auth/csrf") || path.equals("/api/v1/demo/sessions");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof SessionIdentity identity) {
            try {
                companies.requireActive(identity.companyId());
                operators.requireOperator(identity.userId(), identity.companyId());
            } catch (CompanyUnavailableException exception) {
                invalidate(request);
                boolean expired = exception.reason() == CompanyUnavailableException.Reason.EXPIRED_DEMO;
                problems.write(request, response, HttpStatus.UNAUTHORIZED, expired ? "DEMO_EXPIRED" : "UNAUTHENTICATED",
                        expired ? "The demo has expired." : "Authentication is required.");
                return;
            } catch (OperatorUnavailableException exception) {
                invalidate(request);
                problems.write(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.");
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private void invalidate(HttpServletRequest request) {
        if (request.getSession(false) != null) request.getSession(false).invalidate();
        SecurityContextHolder.clearContext();
    }
}
