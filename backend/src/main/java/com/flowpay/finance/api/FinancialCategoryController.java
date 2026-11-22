package com.flowpay.finance.api;

import com.flowpay.finance.application.FinancialCategoryService;
import com.flowpay.finance.application.FinancialCategoryService.CategoryDirection;
import com.flowpay.finance.application.FinancialCategoryService.CategoryPage;
import com.flowpay.finance.application.FinancialCategoryService.CategoryStatus;
import com.flowpay.finance.application.FinancialCategoryService.CategoryView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

@Validated
@RestController
@RequestMapping("/api/financial-categories")
public class FinancialCategoryController {
    private final FinancialCategoryService categories;

    public FinancialCategoryController(FinancialCategoryService categories) { this.categories = categories; }

    @GetMapping
    CategoryPage list(@AuthenticationPrincipal UserDetails user,
                      @RequestParam(required = false) CategoryDirection direction,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "20") int size) {
        return categories.list(user.getUsername(), direction, page, size);
    }

    @PostMapping
    ResponseEntity<CategoryView> create(@AuthenticationPrincipal UserDetails user, @Valid @RequestBody CreateCategoryRequest request) {
        return ResponseEntity.status(201).body(categories.create(user.getUsername(), request.name(), request.direction()));
    }

    @GetMapping("/{id}")
    CategoryView get(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id) {
        return categories.get(user.getUsername(), id);
    }

    @PutMapping("/{id}")
    CategoryView update(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
        return categories.update(user.getUsername(), id, request.name(), request.status());
    }

    record CreateCategoryRequest(@NotBlank @Size(max = 100) String name, @NotNull CategoryDirection direction) {
        @Override public String toString() { return "CreateCategoryRequest[REDACTED]"; }
    }
    record UpdateCategoryRequest(@NotBlank @Size(max = 100) String name, @NotNull CategoryStatus status) {
        @Override public String toString() { return "UpdateCategoryRequest[REDACTED]"; }
    }
}
