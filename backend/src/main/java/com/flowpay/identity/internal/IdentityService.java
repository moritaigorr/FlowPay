package com.flowpay.identity.internal;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.ContractValidationException;
import com.flowpay.identity.IdentityAccess;
import com.flowpay.identity.IdentityProvisioning;
import com.flowpay.identity.InvalidCredentialsException;
import com.flowpay.identity.NewOperator;
import com.flowpay.identity.OperatorUnavailableException;
import com.flowpay.identity.OperatorView;
import com.flowpay.identity.OperatorAlreadyExistsException;
import jakarta.persistence.EntityManager;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
class IdentityService implements IdentityAccess, IdentityProvisioning {
    private static final Pattern OUTER_WHITESPACE = Pattern.compile("\\A\\s+|\\s+\\z", Pattern.UNICODE_CHARACTER_CLASS);
    private final EntityManager entities;
    private final CompanyAccess companies;
    private final PasswordEncoder passwords;
    private final Validator validator;
    private final Clock clock;
    private final String dummyHash;

    IdentityService(EntityManager entities, CompanyAccess companies, PasswordEncoder passwords, Validator validator, Clock clock) {
        this.entities = entities;
        this.companies = companies;
        this.passwords = passwords;
        this.validator = validator;
        this.clock = clock;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    private record EmailValue(@Email String value) {}

    private String email(String value) {
        String normalized = value == null ? "" : OUTER_WHITESPACE.matcher(value).replaceAll("").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ContractValidationException("email", "REQUIRED", "Email is required.");
        }
        if (normalized.codePointCount(0, normalized.length()) > 254) {
            throw new ContractValidationException("email", "TOO_LONG", "Email must contain at most 254 characters.");
        }
        if (!validator.validate(new EmailValue(normalized)).isEmpty()) {
            throw new ContractValidationException("email", "INVALID_FORMAT", "Email must be valid.");
        }
        return normalized;
    }

    @Override
    @Transactional(readOnly = true)
    public OperatorView authenticate(String email, String password) {
        String normalized = email(email);
        if (password == null || password.isEmpty()) {
            throw new ContractValidationException("password", "REQUIRED", "Password is required.");
        }
        if (password.codePointCount(0, password.length()) > 128) {
            throw new ContractValidationException("password", "TOO_LONG", "Password must contain at most 128 characters.");
        }
        var operators = entities.createQuery("select u from OperatorEntity u where u.email = :email", OperatorEntity.class)
                .setParameter("email", normalized).getResultList();
        OperatorEntity operator = operators.isEmpty() ? null : operators.getFirst();
        boolean usable = operator != null && !operator.demo && operator.passwordHash != null;
        boolean matches = passwords.matches(password, usable ? operator.passwordHash : dummyHash);
        if (!usable || !matches) throw new InvalidCredentialsException();
        companies.requireActive(operator.companyId);
        return operator.view();
    }

    @Override
    @Transactional(readOnly = true)
    public OperatorView requireOperator(UUID userId, UUID companyId) {
        OperatorEntity operator = entities.find(OperatorEntity.class, userId);
        if (operator == null || !operator.companyId.equals(companyId)) throw new OperatorUnavailableException();
        return operator.view();
    }

    @Override
    @Transactional
    public OperatorView create(NewOperator command) {
        var company = companies.requireActive(command.companyId());
        if (company.demo() != command.demo() || (command.demo() ? command.passwordHash() != null
                : command.passwordHash() == null || command.passwordHash().isBlank())) {
            throw new ContractValidationException("$", "INVALID_FORMAT", "Invalid operator provisioning configuration.");
        }
        var operator = new OperatorEntity(UUID.randomUUID(), command.companyId(), email(command.email()),
                command.passwordHash(), command.demo(), clock.instant());
        // The global email constraint arbitrates simultaneous provisioning without a logged SQL error.
        entities.flush();
        int inserted = entities.createNativeQuery("""
                INSERT INTO app_user(id, company_id, email, password_hash, demo, created_at)
                VALUES (:id, :companyId, :email, :passwordHash, :demo, :createdAt)
                ON CONFLICT (email) DO NOTHING
                """)
                .setParameter("id", operator.id)
                .setParameter("companyId", operator.companyId)
                .setParameter("email", operator.email)
                .setParameter("passwordHash", operator.passwordHash)
                .setParameter("demo", operator.demo)
                .setParameter("createdAt", operator.createdAt)
                .executeUpdate();
        if (inserted == 0) throw new OperatorAlreadyExistsException();
        return operator.view();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteExpiredDemoOperators(UUID companyId, Instant cutoff) {
        companies.lockExpiredDemo(companyId, cutoff)
                .orElseThrow(() -> new IllegalStateException("Operator cleanup requires a locked expired demo."));
        entities.createQuery("delete from OperatorEntity u where u.companyId=:companyId")
                .setParameter("companyId", companyId).executeUpdate();
    }
}
