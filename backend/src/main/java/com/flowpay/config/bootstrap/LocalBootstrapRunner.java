package com.flowpay.config.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@Profile("local-bootstrap")
class LocalBootstrapRunner implements ApplicationRunner {
    private final LocalBootstrap bootstrap;
    private final Environment environment;

    LocalBootstrapRunner(LocalBootstrap bootstrap, Environment environment) {
        this.bootstrap = bootstrap;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        bootstrap.provision(environment);
    }
}
