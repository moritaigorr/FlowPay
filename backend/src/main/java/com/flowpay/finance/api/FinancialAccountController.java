package com.flowpay.finance.api;

import com.flowpay.finance.application.FinancialAccountService;
import com.flowpay.finance.application.FinancialAccountService.AccountInput;
import com.flowpay.finance.application.FinancialAccountService.AccountPage;
import com.flowpay.finance.application.FinancialAccountService.AccountStatus;
import com.flowpay.finance.application.FinancialAccountService.AccountType;
import com.flowpay.finance.application.FinancialAccountService.AccountView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
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
import org.springframework.http.HttpStatus;

@Validated
@RestController
@RequestMapping("/api/financial-accounts")
public class FinancialAccountController {
    private static final long MAX_AMOUNT_MINOR = 9_000_000_000_000_000L;
    private final FinancialAccountService accounts;

    public FinancialAccountController(FinancialAccountService accounts) { this.accounts = accounts; }

    @GetMapping
    AccountPage list(@AuthenticationPrincipal UserDetails user,
                     @RequestParam(defaultValue = "0") int page,
                     @RequestParam(defaultValue = "20") int size) {
        return accounts.list(user.getUsername(), page, size);
    }

    @PostMapping
    ResponseEntity<AccountView> create(@AuthenticationPrincipal UserDetails user,
                                       @RequestHeader("Idempotency-Key") UUID key,
                                       @Valid @RequestBody AccountRequest request) {
        var result = accounts.create(user.getUsername(), key, request.toInput());
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result.account());
    }

    @GetMapping("/{id}")
    AccountView get(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id) {
        return accounts.get(user.getUsername(), id);
    }

    @PutMapping("/{id}")
    AccountView update(@AuthenticationPrincipal UserDetails user, @PathVariable UUID id,
                       @Valid @RequestBody UpdateAccountRequest request) {
        return accounts.update(user.getUsername(), id, request.name(), request.status());
    }

    record AccountRequest(@NotBlank @Size(max = 120) String name,
                          @NotNull AccountType type,
                          @NotBlank @Pattern(regexp = "-?[0-9]{1,16}") String openingBalanceMinor,
                          @NotNull LocalDate openingBalanceDate) {
        AccountInput toInput() {
            final long amount;
            try { amount = Long.parseLong(openingBalanceMinor); }
            catch (NumberFormatException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo inicial fora do limite."); }
            if (Math.abs(amount) > MAX_AMOUNT_MINOR) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saldo inicial fora do limite.");
            return new AccountInput(name, type, amount, openingBalanceDate);
        }
        @Override public String toString() { return "AccountRequest[REDACTED]"; }
    }

    record UpdateAccountRequest(@NotBlank @Size(max = 120) String name, @NotNull AccountStatus status) {
        @Override public String toString() { return "UpdateAccountRequest[REDACTED]"; }
    }
}
