package com.flowpay.finance.api;

import com.flowpay.finance.application.CashTransactionService;
import com.flowpay.finance.application.CashTransactionService.Direction;
import com.flowpay.finance.application.CashTransactionService.TransactionInput;
import com.flowpay.finance.application.CashTransactionService.TransactionPage;
import com.flowpay.finance.application.CashTransactionService.TransactionView;
import com.flowpay.finance.application.CashTransactionService.TransferInput;
import com.flowpay.finance.application.CashTransactionService.TransferResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping("/api/cash-transactions")
public class CashTransactionController {
    private static final long MAX_AMOUNT_MINOR = 9_000_000_000_000_000L;
    private final CashTransactionService transactions;

    public CashTransactionController(CashTransactionService transactions) { this.transactions = transactions; }

    @GetMapping
    TransactionPage list(@AuthenticationPrincipal UserDetails user,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "20") int size) {
        return transactions.list(user.getUsername(), page, size);
    }

    @PostMapping
    ResponseEntity<TransactionView> create(@AuthenticationPrincipal UserDetails user,
                                           @RequestHeader("Idempotency-Key") UUID key,
                                           @Valid @RequestBody TransactionRequest request) {
        var result = transactions.create(user.getUsername(), key, request.toInput());
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result.transaction());
    }

    @PostMapping("/transfers")
    ResponseEntity<TransferResult> transfer(@AuthenticationPrincipal UserDetails user,
                                           @RequestHeader("Idempotency-Key") UUID key,
                                           @Valid @RequestBody TransferRequest request) {
        var result = transactions.transfer(user.getUsername(), key, request.toInput());
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    record TransactionRequest(@NotNull Direction direction,
                              @NotNull UUID accountId,
                              @NotNull UUID categoryId,
                              @NotBlank @Pattern(regexp = "[0-9]{1,16}") String amountMinor,
                              @NotNull LocalDate effectiveDate,
                              @NotBlank @Size(max = 160) String description) {
        TransactionInput toInput() {
            final long amount;
            try { amount = Long.parseLong(amountMinor); }
            catch (NumberFormatException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor fora do limite permitido."); }
            if (amount <= 0 || amount > MAX_AMOUNT_MINOR) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor precisa ser positivo e estar dentro do limite permitido.");
            return new TransactionInput(direction, accountId, categoryId, amount, effectiveDate, description);
        }
        @Override public String toString() { return "TransactionRequest[REDACTED]"; }
    }

    record TransferRequest(@NotNull UUID sourceAccountId,
                           @NotNull UUID destinationAccountId,
                           @NotBlank @Pattern(regexp = "[0-9]{1,16}") String amountMinor,
                           @NotNull LocalDate effectiveDate,
                           @NotBlank @Size(max = 160) String description) {
        TransferInput toInput() {
            final long amount;
            try { amount = Long.parseLong(amountMinor); }
            catch (NumberFormatException invalid) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor fora do limite permitido."); }
            if (amount <= 0 || amount > MAX_AMOUNT_MINOR) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor precisa ser positivo e estar dentro do limite permitido.");
            return new TransferInput(sourceAccountId, destinationAccountId, amount, effectiveDate, description);
        }
        @Override public String toString() { return "TransferRequest[REDACTED]"; }
    }
}
