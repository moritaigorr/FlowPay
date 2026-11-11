package com.flowpay.config.web;

import com.flowpay.config.security.SessionIdentity;
import com.flowpay.company.ContractValidationException;
import com.flowpay.customer.CustomerListing;
import com.flowpay.customer.CustomerOperations;
import com.flowpay.customer.CustomerPage;
import com.flowpay.customer.CustomerView;
import com.flowpay.customer.NewCustomer;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

@RestController
class CustomerController {
    private final CustomerOperations customers;
    private final CustomerListing listing;

    CustomerController(CustomerOperations customers, CustomerListing listing) {
        this.customers = customers;
        this.listing = listing;
    }

    record CreateCustomerRequest(JsonNode name, JsonNode email) {}

    @PostMapping("/api/v1/customers")
    @ResponseStatus(HttpStatus.CREATED)
    CustomerView create(@AuthenticationPrincipal SessionIdentity identity, @RequestBody CreateCustomerRequest body) {
        return customers.create(identity.companyId(), new NewCustomer(text(body.name(), "name"), text(body.email(), "email")));
    }

    @GetMapping("/api/v1/customers")
    CustomerPage list(@AuthenticationPrincipal SessionIdentity identity,
            @RequestParam(required = false) String page, @RequestParam(required = false) String size,
            @RequestParam(required = false) String q) {
        return listing.list(identity.companyId(), pagination(page, "page", 0), pagination(size, "size", 20), q);
    }

    private int pagination(String value, String field, int fallback) {
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw new ContractValidationException(field, "INVALID_FORMAT", "An integer value is required.");
        }
    }

    private String text(JsonNode value, String field) {
        if (value == null || value.isNull()) return null;
        if (!value.isString()) {
            throw new ContractValidationException(field, "INVALID_FORMAT", "A string value is required.");
        }
        return value.asString();
    }
}
