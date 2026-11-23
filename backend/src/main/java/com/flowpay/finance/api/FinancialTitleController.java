package com.flowpay.finance.api;

import com.flowpay.finance.application.FinancialTitleService;
import com.flowpay.finance.application.FinancialTitleService.TitleInput;
import com.flowpay.finance.application.FinancialTitleService.TitleKind;
import com.flowpay.finance.application.FinancialTitleService.TitleStatus;
import com.flowpay.finance.application.FinancialTitleService.TitlePage;
import com.flowpay.finance.application.FinancialTitleService.SettlementInput;
import com.flowpay.finance.application.FinancialTitleService.SettlementResult;
import com.flowpay.finance.application.FinancialTitleService.SettlementView;
import com.flowpay.finance.application.FinancialTitleService.ReversalInput;
import com.flowpay.finance.application.FinancialTitleService.TitleView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/financial-titles")
public class FinancialTitleController {
    private static final long MAX_AMOUNT_MINOR = 9_000_000_000_000_000L;
    private final FinancialTitleService titles;

    public FinancialTitleController(FinancialTitleService titles) { this.titles = titles; }

    @GetMapping
    TitlePage list(@AuthenticationPrincipal UserDetails user,
                   @RequestParam TitleKind kind,
                   @RequestParam(required = false) @Size(max = 100) String q,
                   @RequestParam(required = false) TitleStatus status,
                   @RequestParam(required = false) UUID contactId,
                   @RequestParam(required = false) LocalDate dueFrom,
                   @RequestParam(required = false) LocalDate dueTo,
                   @RequestParam(defaultValue = "0") int page,
                   @RequestParam(defaultValue = "20") int size) {
        return titles.list(user.getUsername(), kind, q, status, contactId, dueFrom, dueTo, page, size);
    }

    @PostMapping
    ResponseEntity<TitleView> create(@AuthenticationPrincipal UserDetails user,
                                     @RequestHeader("Idempotency-Key") UUID key,
                                     @Valid @RequestBody TitleRequest request) {
        var result = titles.create(user.getUsername(), request.kind(), key, request.toInput());
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result.title());
    }

    @GetMapping("/{id}")
    TitleView get(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id) {
        return titles.get(user.getUsername(), id);
    }

    @PutMapping("/{id}")
    TitleView update(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id,
                     @Valid @RequestBody UpdateTitleRequest request) {
        return titles.update(user.getUsername(), id, request.toInput());
    }

    @PostMapping("/{id}/cancel")
    TitleView cancel(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id,
                     @Valid @RequestBody CancelTitleRequest request) {
        return titles.cancel(user.getUsername(), id, request.reason());
    }

    @PostMapping("/{id}/settlements")
    ResponseEntity<TitleView> settle(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id,
                                     @RequestHeader("Idempotency-Key") UUID key,
                                     @Valid @RequestBody SettlementRequest request) {
        var result = titles.settle(user.getUsername(), id, key, request.toInput());
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result.title());
    }

    @GetMapping("/{id}/settlements")
    List<SettlementView> listSettlements(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id) {
        return titles.listSettlements(user.getUsername(), id);
    }

    @PostMapping("/{id}/settlements/{settlementId}/reversals")
    ResponseEntity<TitleView> reverse(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id,
                                      @PathVariable UUID settlementId, @RequestHeader("Idempotency-Key") UUID key,
                                      @Valid @RequestBody ReversalRequest request) {
        var result = titles.reverse(user.getUsername(), id, settlementId, key, request.toInput());
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result.title());
    }

    record SettlementRequest(@NotNull UUID accountId,
                             @NotBlank @Pattern(regexp = "[0-9]{1,16}") String amountMinor,
                             @NotNull LocalDate effectiveDate) {
        SettlementInput toInput() { return new SettlementInput(accountId, parseAmount(amountMinor), effectiveDate); }
        @Override public String toString() { return "SettlementRequest[REDACTED]"; }
    }

    record ReversalRequest(@NotBlank @Pattern(regexp = "[0-9]{1,16}") String amountMinor,
                           @NotNull LocalDate effectiveDate,
                           @NotBlank @Size(max = 500) String reason) {
        ReversalInput toInput() { return new ReversalInput(parseAmount(amountMinor), effectiveDate, reason); }
        @Override public String toString() { return "ReversalRequest[REDACTED]"; }
    }

    record TitleRequest(@NotNull TitleKind kind,
                        @NotBlank @Size(max = 180) String description,
                        @NotNull UUID contactId,
                        @NotNull UUID categoryId,
                        UUID plannedAccountId,
                        @NotBlank @Pattern(regexp = "[0-9]{1,16}") String originalAmountMinor,
                        @NotNull LocalDate dueDate,
                        @Size(max = 1000) String notes) {
        TitleInput toInput() {
            return new TitleInput(description, contactId, categoryId, plannedAccountId,
                    parseAmount(originalAmountMinor), dueDate, notes);
        }
        @Override public String toString() { return "TitleRequest[REDACTED]"; }
    }

    record UpdateTitleRequest(@NotBlank @Size(max = 180) String description,
                              @NotNull UUID contactId,
                              @NotNull UUID categoryId,
                              UUID plannedAccountId,
                              @NotBlank @Pattern(regexp = "[0-9]{1,16}") String originalAmountMinor,
                              @NotNull LocalDate dueDate,
                              @Size(max = 1000) String notes) {
        TitleInput toInput() {
            return new TitleInput(description, contactId, categoryId, plannedAccountId,
                    parseAmount(originalAmountMinor), dueDate, notes);
        }
        @Override public String toString() { return "UpdateTitleRequest[REDACTED]"; }
    }

    record CancelTitleRequest(@NotBlank @Size(max = 500) String reason) {
        @Override public String toString() { return "CancelTitleRequest[REDACTED]"; }
    }

    private static long parseAmount(String value) {
        final long amount;
        try { amount = Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor fora do limite permitido."); }
        if (amount <= 0 || amount > MAX_AMOUNT_MINOR) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor precisa ser positivo e estar dentro do limite permitido.");
        }
        return amount;
    }
}
