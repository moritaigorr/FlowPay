package com.flowpay.reconciliation.api;

import com.flowpay.reconciliation.application.ReconciliationSuggestionService;
import com.flowpay.reconciliation.application.ReconciliationMatchService;
import com.flowpay.reconciliation.application.ReconciliationMatchService.MatchRequest;
import com.flowpay.reconciliation.application.ReconciliationMatchService.MatchResult;
import com.flowpay.reconciliation.application.ReconciliationMatchService.UnlinkRequest;
import com.flowpay.reconciliation.application.ReconciliationMatchService.UnlinkResult;
import com.flowpay.reconciliation.application.ReconciliationCandidateIgnoreService;
import com.flowpay.reconciliation.application.ReconciliationCandidateIgnoreService.CandidateRequest;
import com.flowpay.reconciliation.application.ReconciliationCandidateIgnoreService.CandidateResult;
import java.util.UUID;
import java.util.List;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/api/statement-lines")
public class ReconciliationController {
    private final ReconciliationSuggestionService suggestions;
    private final ReconciliationMatchService matches;
    private final com.flowpay.reconciliation.application.ReconciliationStatusService statuses;
    private final ReconciliationCandidateIgnoreService candidateIgnores;

    public ReconciliationController(ReconciliationSuggestionService suggestions, ReconciliationMatchService matches,
                                   com.flowpay.reconciliation.application.ReconciliationStatusService statuses,
                                   ReconciliationCandidateIgnoreService candidateIgnores) {
        this.suggestions = suggestions;
        this.matches = matches;
        this.statuses = statuses;
        this.candidateIgnores = candidateIgnores;
    }

    @GetMapping("/{lineId}/suggestions")
    ReconciliationSuggestionService.SuggestionList suggestions(@AuthenticationPrincipal UserDetails user,
                                                                 @PathVariable UUID lineId) {
        return suggestions.suggest(user.getUsername(), lineId);
    }

    @PostMapping("/{lineId}/match")
    ResponseEntity<MatchResult> match(@AuthenticationPrincipal UserDetails user,
                                      @PathVariable UUID lineId,
                                      @RequestHeader("Idempotency-Key") UUID idempotencyKey,
                                      @Valid @RequestBody MatchRequest request) {
        var result = matches.match(user.getUsername(), lineId, idempotencyKey, request);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{lineId}/match/reversal")
    ResponseEntity<UnlinkResult> unlink(@AuthenticationPrincipal UserDetails user, @PathVariable UUID lineId,
            @RequestHeader("Idempotency-Key") UUID idempotencyKey, @RequestBody UnlinkRequest request) {
        var result = matches.unlink(user.getUsername(), lineId, idempotencyKey, request);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{lineId}/candidate-ignores/{movementId}")
    ResponseEntity<CandidateResult> ignoreCandidate(@AuthenticationPrincipal UserDetails user, @PathVariable UUID lineId,
            @PathVariable UUID movementId, @RequestHeader("Idempotency-Key") UUID key, @RequestBody CandidateRequest request) {
        var result = candidateIgnores.ignore(user.getUsername(), lineId, movementId, key, request);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @PostMapping("/{lineId}/candidate-ignores/{movementId}/restore")
    ResponseEntity<CandidateResult> restoreCandidate(@AuthenticationPrincipal UserDetails user, @PathVariable UUID lineId,
            @PathVariable UUID movementId, @RequestHeader("Idempotency-Key") UUID key, @RequestBody CandidateRequest request) {
        var result = candidateIgnores.restore(user.getUsername(), lineId, movementId, key, request);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }

    @GetMapping("/matches")
    List<com.flowpay.reconciliation.api.ReconciliationMatchStatusReader.ActiveMatch> activeMatches(
            @AuthenticationPrincipal UserDetails user, @RequestParam List<UUID> lineIds) {
        return statuses.activeMatches(user.getUsername(), lineIds);
    }
}
