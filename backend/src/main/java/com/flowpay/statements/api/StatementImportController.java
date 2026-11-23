package com.flowpay.statements.api;

import com.flowpay.statements.application.StatementPreviewService;
import com.flowpay.statements.application.StatementReconciliationSummaryService;
import com.flowpay.statements.domain.StatementCsvParser;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/statement-imports")
public class StatementImportController {
    private final StatementPreviewService previews;
    private final StatementReconciliationSummaryService summaries;

    public StatementImportController(StatementPreviewService previews, StatementReconciliationSummaryService summaries) {
        this.previews = previews; this.summaries = summaries;
    }

    @PostMapping("/preview")
    StatementCsvParser.Preview preview(@AuthenticationPrincipal UserDetails user,
                                       @RequestParam UUID accountId,
                                       @RequestParam MultipartFile file) {
        var content = readFile(file);
        return previews.preview(user.getUsername(), accountId, content);
    }

    @PostMapping("/confirm")
    ResponseEntity<StatementPreviewService.ConfirmationResult> confirm(@AuthenticationPrincipal UserDetails user,
                                                                       @RequestParam UUID accountId,
                                                                       @RequestParam MultipartFile file) {
        var result = previews.confirm(user.getUsername(), accountId, readFile(file));
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @GetMapping
    StatementPreviewService.ImportPage list(@AuthenticationPrincipal UserDetails user,
                                            @RequestParam UUID accountId,
                                            @RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "20") int size) {
        return previews.listImports(user.getUsername(), accountId, page, size);
    }

    @GetMapping("/{importId}/lines")
    StatementPreviewService.LinePage lines(@AuthenticationPrincipal UserDetails user,
                                           @PathVariable UUID importId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return previews.listLines(user.getUsername(), importId, page, size);
    }

    @GetMapping("/{importId}/reconciliation-summary")
    StatementReconciliationSummaryService.Summary reconciliationSummary(@AuthenticationPrincipal UserDetails user,
            @PathVariable UUID importId, @RequestParam String timezone) {
        return summaries.get(user.getUsername(), importId, timezone);
    }

    private static byte[] readFile(MultipartFile file) {
        if (file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um arquivo CSV.");
        if (file.getSize() > StatementCsvParser.MAX_FILE_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo excede o limite de 10 MB.");
        try { return file.getBytes(); }
        catch (IOException failure) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo enviado."); }
    }
}
