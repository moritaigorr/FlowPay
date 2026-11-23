package com.flowpay.reconciliation.application;

import com.flowpay.finance.api.FinanceReconciliationReader;
import com.flowpay.identity.api.TenantAccess;
import com.flowpay.reconciliation.api.ReconciliationMatchStatusReader;
import com.flowpay.statements.api.StatementLineReader;
import java.text.Normalizer;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReconciliationSuggestionService {
    private static final int DATE_WINDOW_DAYS = 3;
    private static final int CANDIDATE_LIMIT = 100;
    private static final int SUGGESTION_LIMIT = 10;
    private final TenantAccess tenantAccess;
    private final FinanceReconciliationReader finance;
    private final StatementLineReader statementLines;
    private final ReconciliationMatchStatusReader matchStatuses;
    private final ReconciliationCandidateIgnoreService candidateIgnores;

    public ReconciliationSuggestionService(TenantAccess tenantAccess, FinanceReconciliationReader finance,
                                           StatementLineReader statementLines, ReconciliationMatchStatusReader matchStatuses,
                                           ReconciliationCandidateIgnoreService candidateIgnores) {
        this.tenantAccess = tenantAccess;
        this.finance = finance;
        this.statementLines = statementLines;
        this.matchStatuses = matchStatuses;
        this.candidateIgnores = candidateIgnores;
    }

    @Transactional(readOnly = true)
    public SuggestionList suggest(String email, UUID lineId) {
        var tenant = tenantAccess.requireSingleTenant(email);
        var line = statementLines.findLine(tenant.id(), lineId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha válida não encontrada."));
        if (!"ACCEPTED".equals(line.status()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Linha válida não encontrada.");
        if ("IGNORED".equals(line.reviewStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta linha foi ignorada durante a revisão e não recebe sugestões.");
        if (!matchStatuses.activeMatches(tenant.id(), List.of(lineId)).isEmpty())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Esta linha já possui uma conciliação ativa.");
        var candidateSearch = finance.matchingMovements(tenant.id(), line.accountId(), line.direction(),
                Long.parseLong(line.amountMinor()), line.date().minusDays(DATE_WINDOW_DAYS),
                line.date().plusDays(DATE_WINDOW_DAYS), CANDIDATE_LIMIT);
        var activeMovementIds = matchStatuses.activeMovementIds(tenant.id(), candidateSearch.items().stream()
                .map(FinanceReconciliationReader.MovementCandidate::id).toList());
        var ignoredCandidates = candidateIgnores.active(tenant.id(), lineId);
        var ignoredIds = ignoredCandidates.stream().map(candidate -> candidate.movement().id()).collect(java.util.stream.Collectors.toSet());
        var availableCandidates = candidateSearch.items().stream().filter(candidate -> !activeMovementIds.contains(candidate.id())
                && !ignoredIds.contains(candidate.id())).toList();
        var suggestions = availableCandidates.stream().map(candidate -> score(line, candidate))
                .sorted(java.util.Comparator.comparingInt(Suggestion::score).reversed()
                        .thenComparingLong(Suggestion::dateDistanceDays)
                        .thenComparing(suggestion -> suggestion.movement().id()))
                .limit(SUGGESTION_LIMIT).toList();
        return new SuggestionList(lineId, suggestions, availableCandidates.size(), candidateSearch.total() > candidateSearch.items().size(), ignoredCandidates,
                "Sugestões ordenadas por valor, direção, data e descrição; escolha manual é necessária.");
    }

    private static Suggestion score(StatementLineReader.StatementLine line, FinanceReconciliationReader.MovementCandidate movement) {
        long distance = Math.abs(ChronoUnit.DAYS.between(line.date(), movement.effectiveDate()));
        int dateScore = distance == 0 ? 20 : distance == 1 ? 15 : distance == 2 ? 10 : 5;
        double descriptionOverlap = jaccard(tokens(line.description()), tokens(movement.description()));
        int descriptionScore = (int) Math.floor(descriptionOverlap * 5);
        var evidence = new java.util.ArrayList<String>();
        evidence.add("Mesmo valor");
        evidence.add("Mesma direção");
        evidence.add(distance == 0 ? "Data igual" : "Data com diferença de " + distance + " dias");
        if (descriptionScore > 0) evidence.add("Descrição semelhante");
        return new Suggestion(movement, 50 + 25 + dateScore + descriptionScore, distance,
                descriptionScore, List.copyOf(evidence));
    }

    private static Set<String> tokens(String value) {
        var normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
        return new HashSet<>(Arrays.stream(normalized.split("[^a-z0-9]+"))
                .filter(token -> token.length() >= 3).toList());
    }

    private static double jaccard(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        var intersection = new HashSet<>(left);
        intersection.retainAll(right);
        var union = new HashSet<>(left);
        union.addAll(right);
        return (double) intersection.size() / union.size();
    }

    public record Suggestion(FinanceReconciliationReader.MovementCandidate movement, int score, long dateDistanceDays,
                             int descriptionScore, List<String> evidence) {}
    public record SuggestionList(UUID statementLineId, List<Suggestion> suggestions, long candidateCount,
                                 boolean candidateSetTruncated,
                                 List<ReconciliationCandidateIgnoreService.SuppressedCandidate> ignoredCandidates,
                                 String explanation) {}
}
