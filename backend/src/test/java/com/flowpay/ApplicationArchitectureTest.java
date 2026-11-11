package com.flowpay;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import static org.assertj.core.api.Assertions.assertThat;

class ApplicationArchitectureTest {
    @Test
    void domainModulesHaveNoCyclesOrInternalAccess() {
        var modules = ApplicationModules.of(FlowPayApplication.class);
        assertThat(modules.stream().map(m -> m.getIdentifier().toString()))
                .containsExactlyInAnyOrder("company", "identity", "customer", "payment");
        modules.verify();
    }
}
