package com.flowpay.identity.internal;

import com.flowpay.identity.OperatorView;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "app_user")
class OperatorEntity {
    @Id UUID id;
    UUID companyId;
    String email;
    String passwordHash;
    boolean demo;
    Instant createdAt;

    protected OperatorEntity() {}

    OperatorEntity(UUID id, UUID companyId, String email, String passwordHash, boolean demo, Instant createdAt) {
        this.id = id;
        this.companyId = companyId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.demo = demo;
        this.createdAt = createdAt;
    }

    OperatorView view() {
        return new OperatorView(id, companyId, email, createdAt);
    }
}
