package com.flowpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SimpleCommandLinePropertySource;

@SpringBootApplication
public class FlowPayApplication {
    public static void main(String[] args) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource(args));
        boolean bootstrap = java.util.Arrays.asList(environment.getActiveProfiles()).contains("local-bootstrap");
        var application = new SpringApplication(FlowPayApplication.class);
        if (bootstrap) application.setWebApplicationType(WebApplicationType.NONE);
        var context = application.run(args);
        if (bootstrap) context.close();
    }
}
