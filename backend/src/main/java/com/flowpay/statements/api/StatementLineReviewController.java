package com.flowpay.statements.api;

import com.flowpay.statements.application.StatementLineReviewService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/statement-lines")
public class StatementLineReviewController {
    private final StatementLineReviewService reviews;
    public StatementLineReviewController(StatementLineReviewService reviews) { this.reviews = reviews; }
    @PostMapping("/{lineId}/review")
    ResponseEntity<StatementLineReviewService.ReviewResult> review(@AuthenticationPrincipal UserDetails user,
            @PathVariable UUID lineId, @RequestHeader("Idempotency-Key") UUID key,
            @RequestBody StatementLineReviewService.ReviewRequest request) {
        var result = reviews.review(user.getUsername(), lineId, key, request);
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.CREATED).body(result);
    }
}
