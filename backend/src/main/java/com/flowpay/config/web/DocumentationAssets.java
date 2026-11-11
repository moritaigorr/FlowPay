package com.flowpay.config.web;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class DocumentationAssets implements WebMvcConfigurer {
    private final String swaggerLocation;

    DocumentationAssets() throws IOException {
        // Disabling generated API docs also disables springdoc's asset mapping.
        // Discover the single bundled WebJar instead of duplicating its transitive version.
        var assets = new PathMatchingResourcePatternResolver().getResources(
                "classpath*:/META-INF/resources/webjars/swagger-ui/*/swagger-ui.css");
        if (assets.length != 1) {
            throw new IllegalStateException("Exactly one bundled Swagger UI is required");
        }
        String stylesheet = assets[0].getURL().toExternalForm();
        swaggerLocation = stylesheet.substring(0, stylesheet.lastIndexOf('/') + 1);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/swagger-ui/**")
                .addResourceLocations("classpath:/static/swagger-ui/", swaggerLocation);
    }
}
