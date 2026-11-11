package com.flowpay.config.bootstrap;

import com.flowpay.company.CompanyProvisioning;
import com.flowpay.company.NewCompany;
import com.flowpay.identity.IdentityProvisioning;
import com.flowpay.identity.NewOperator;
import com.flowpay.identity.OperatorAlreadyExistsException;
import java.util.Arrays;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class LocalBootstrap {
    private final CompanyProvisioning companies;
    private final IdentityProvisioning operators;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transaction;

    public LocalBootstrap(CompanyProvisioning companies, IdentityProvisioning operators,
            PasswordEncoder passwords, PlatformTransactionManager transactions) {
        this.companies = companies;
        this.operators = operators;
        this.passwords = passwords;
        this.transaction = new TransactionTemplate(transactions);
    }

    public void provision(Environment environment) {
        var profiles = Arrays.asList(environment.getActiveProfiles());
        if (!profiles.contains("local-bootstrap") || profiles.stream().anyMatch(profile ->
                !profile.equals("local-bootstrap") && !profile.equals("local"))
                || !"true".equals(environment.getProperty("FLOWPAY_BOOTSTRAP_ENABLED"))) {
            throw new IllegalStateException("Local bootstrap requires its explicit local-only profile and enablement.");
        }
        String name = required(environment, "FLOWPAY_BOOTSTRAP_COMPANY_NAME");
        String email = required(environment, "FLOWPAY_BOOTSTRAP_EMAIL");
        String password = required(environment, "FLOWPAY_BOOTSTRAP_PASSWORD");
        if (password.codePointCount(0, password.length()) > 128) {
            throw new IllegalStateException("Bootstrap password must contain 1 to 128 characters.");
        }
        String hash = passwords.encode(password);
        try {
            transaction.executeWithoutResult(status -> {
                var company = companies.create(new NewCompany(name, false));
                operators.create(new NewOperator(company.id(), email, hash, false));
            });
        } catch (OperatorAlreadyExistsException exception) {
            // Duplicate email refuses the entire command; never adopt/reset an existing account.
            throw new IllegalStateException("Local provisioning refused; no account was created or modified.");
        }
    }

    private String required(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isEmpty()) throw new IllegalStateException("Missing explicit bootstrap configuration: " + key);
        return value;
    }
}
