package com.flowpay.config.security;

import com.flowpay.config.web.ApiProblems;
import com.flowpay.company.CompanyAccess;
import com.flowpay.identity.IdentityAccess;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class SecurityConfiguration {
    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokens) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrfTokens)));
    }

    @Bean
    AuthenticationManager authenticationManager(IdentityAccess operators) {
        return authentication -> {
            var operator = operators.authenticate(authentication.getName(), (String) authentication.getCredentials());
            return UsernamePasswordAuthenticationToken.authenticated(
                    new SessionIdentity(operator.id(), operator.companyId()), null, List.of());
        };
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, ApiProblems problems,
            CsrfTokenRepository csrfTokens, SecurityContextRepository contexts,
            CompanyAccess companies, IdentityAccess operators) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/api/openapi.yaml",
                        "/api/docs", "/swagger-ui.html", "/swagger-ui/**", "/api/v1/auth/csrf").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/demo/sessions").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().denyAll());
        http.csrf(csrf -> csrf.csrfTokenRepository(csrfTokens));
        http.securityContext(context -> context.securityContextRepository(contexts));
        http.exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, exception) -> problems.write(
                        request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required."))
                .accessDeniedHandler((request, response, exception) -> problems.write(
                        request, response, HttpStatus.FORBIDDEN, "CSRF_INVALID", "The request is not permitted.")));
        http.addFilterBefore(new ActiveCompanyFilter(companies, operators, problems), AuthorizationFilter.class);
        http.requestCache(cache -> cache.disable());
        http.logout(logout -> logout.disable());
        http.formLogin(form -> form.disable());
        http.httpBasic(basic -> basic.disable());
        return http.build();
    }
}
