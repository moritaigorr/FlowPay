package com.flowpay.config.demo;

import com.flowpay.company.CompanyAccess;
import com.flowpay.company.CompanyProvisioning;
import com.flowpay.company.CompanyView;
import com.flowpay.identity.IdentityAccess;
import com.flowpay.identity.IdentityProvisioning;
import com.flowpay.identity.NewOperator;
import com.flowpay.identity.OperatorView;
import com.flowpay.customer.CustomerOperations;
import com.flowpay.customer.NewCustomer;
import com.flowpay.payment.CreatedPayment;
import com.flowpay.payment.NewPayment;
import com.flowpay.payment.PaymentOperations;
import com.flowpay.payment.SimulationScenario;
import com.flowpay.config.security.SessionIdentity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DemoSessionService {
    public record Session(OperatorView user, CompanyView company, boolean created) {}
    private record SeedCustomer(String name, String email, BigDecimal amount,
            SimulationScenario scenario, String description, String transition) {}

    private final CompanyAccess companies;
    private final CompanyProvisioning companyProvisioning;
    private final IdentityAccess identities;
    private final IdentityProvisioning identityProvisioning;
    private final CustomerOperations customers;
    private final PaymentOperations payments;
    private final JsonMapper mapper;
    private final int activeCapacity;

    public DemoSessionService(CompanyAccess companies, CompanyProvisioning companyProvisioning,
            IdentityAccess identities, IdentityProvisioning identityProvisioning,
            CustomerOperations customers, PaymentOperations payments, JsonMapper mapper,
            @Value("${flowpay.demo.max-active-companies:500}") int activeCapacity) {
        this.companies = companies;
        this.companyProvisioning = companyProvisioning;
        this.identities = identities;
        this.identityProvisioning = identityProvisioning;
        this.customers = customers;
        this.payments = payments;
        this.mapper = mapper;
        this.activeCapacity = activeCapacity;
    }

    @Transactional(readOnly = true)
    public Optional<Session> reuseCurrent(SessionIdentity current) {
        if (current == null) return Optional.empty();
        CompanyView company = companies.requireActive(current.companyId());
        if (!company.demo()) return Optional.empty();
        OperatorView user = identities.requireOperator(current.userId(), current.companyId());
        return Optional.of(new Session(user, company, false));
    }

    @Transactional
    public Session createFresh() {
        CompanyView company = companyProvisioning.createDemoIfAvailable(activeCapacity)
                .orElseThrow(DemoCapacityReachedException::new);
        String syntheticEmail = "demo-" + UUID.randomUUID() + "@demo.flowpay.invalid";
        OperatorView user = identityProvisioning.create(new NewOperator(company.id(), syntheticEmail, null, true));
        seed(company, user);
        return new Session(user, company, true);
    }

    private void seed(CompanyView company, OperatorView user) {
        UUID companyId = company.id();
        String suffix = companyId.toString();
        List<SeedCustomer> fixtures = List.of(
                new SeedCustomer("Aster Coffee", "aster-" + suffix + "@example.test", new BigDecimal("150.00"),
                        SimulationScenario.APPROVE, "Daily batch settlement", "process"),
                new SeedCustomer("Northstar Goods", "northstar-" + suffix + "@example.test", new BigDecimal("20.00"),
                        SimulationScenario.DECLINE, "Risk review sample", "process"),
                new SeedCustomer("Juniper Market", "juniper-" + suffix + "@example.test", new BigDecimal("30.00"),
                        SimulationScenario.APPROVE, "Waiting for processing", null),
                new SeedCustomer("Cedar Studio", "cedar-" + suffix + "@example.test", new BigDecimal("40.00"),
                        SimulationScenario.APPROVE, "Customer cancelled", "cancel"));

        for (SeedCustomer fixture : fixtures) {
            var customer = customers.create(companyId, new NewCustomer(fixture.name(), fixture.email()));
            CreatedPayment created = payments.create(companyId, user.id(), "demo-seed-" + UUID.randomUUID(),
                    new NewPayment(customer.id(), fixture.amount(), "BRL", fixture.description(), fixture.scenario()));
            UUID paymentId = paymentId(created);
            if ("process".equals(fixture.transition())) payments.process(companyId, user.id(), paymentId);
            else if ("cancel".equals(fixture.transition())) payments.cancel(companyId, user.id(), paymentId);
        }
    }

    private UUID paymentId(CreatedPayment created) {
        try {
            return UUID.fromString(mapper.readTree(created.responseJson()).path("id").asString());
        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("The seeded payment response was invalid.", exception);
        }
    }
}
