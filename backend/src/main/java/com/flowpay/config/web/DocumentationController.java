package com.flowpay.config.web;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
class DocumentationController {
    @GetMapping(value = "/api/openapi.yaml", produces = "application/yaml")
    @ResponseBody
    Resource schema() {
        return new ClassPathResource("static/api/openapi.yaml");
    }

    @GetMapping("/swagger-ui.html")
    String legacyDocumentation() {
        return "redirect:/api/docs";
    }

    @GetMapping(value = "/api/docs", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    Resource documentation() {
        return new ClassPathResource("documentation/index.html");
    }
}
