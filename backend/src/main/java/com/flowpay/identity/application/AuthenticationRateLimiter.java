package com.flowpay.identity.application;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationRateLimiter {
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private final JdbcTemplate jdbc;
    private final SecretKeySpec key;

    public AuthenticationRateLimiter(JdbcTemplate jdbc,
                                     @Value("${flowpay.security.rate-limit-hmac-key}") String secret) {
        if (secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("FLOWPAY_RATE_LIMIT_HMAC_KEY must contain at least 32 UTF-8 bytes.");
        }
        this.jdbc = jdbc;
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Decision reserve(String scope, String identity, int maximumAttempts, Duration window) {
        if (maximumAttempts < 1 || maximumAttempts > 1000 || window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("Invalid authentication rate limit policy.");
        }
        var seconds = Math.max(1L, window.toSeconds());
        var result = jdbc.queryForMap("""
                INSERT INTO authentication_rate_limit AS current_bucket
                    (bucket_hash, window_started_at, attempt_count, updated_at)
                VALUES (?, clock_timestamp(), 1, clock_timestamp())
                ON CONFLICT (bucket_hash) DO UPDATE SET
                    window_started_at = CASE
                        WHEN current_bucket.window_started_at <= clock_timestamp() - (? * INTERVAL '1 second')
                        THEN clock_timestamp() ELSE current_bucket.window_started_at END,
                    attempt_count = CASE
                        WHEN current_bucket.window_started_at <= clock_timestamp() - (? * INTERVAL '1 second')
                        THEN 1 ELSE LEAST(current_bucket.attempt_count + 1, ?) END,
                    updated_at = clock_timestamp()
                RETURNING attempt_count,
                    GREATEST(1, CEIL(EXTRACT(EPOCH FROM
                        (window_started_at + (? * INTERVAL '1 second') - clock_timestamp())))::integer) AS retry_after_seconds
                """, fingerprint(scope, identity), seconds, seconds, maximumAttempts + 1, seconds);
        int attempts = ((Number) result.get("attempt_count")).intValue();
        int retryAfter = ((Number) result.get("retry_after_seconds")).intValue();
        return new Decision(attempts <= maximumAttempts, retryAfter);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void clear(String scope, String identity) {
        jdbc.update("DELETE FROM authentication_rate_limit WHERE bucket_hash = ?", fingerprint(scope, identity));
    }

    private String fingerprint(String scope, String identity) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal((scope + "\u0000" + identity).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", impossible);
        }
    }

    public record Decision(boolean allowed, int retryAfterSeconds) {}
}
