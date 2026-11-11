package com.flowpay.company.internal;

import com.flowpay.company.CompanyView;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company")
class CompanyEntity {
    @Id UUID id;
    String name;
    Instant createdAt;
    boolean demo;
    Instant expiresAt;

    protected CompanyEntity() {}

    CompanyEntity(UUID id, String name, Instant createdAt, boolean demo, Instant expiresAt) {
        this.id = id;
        this.name = name;
        this.createdAt = createdAt;
        this.demo = demo;
        this.expiresAt = expiresAt;
    }

    CompanyView view() {
        return new CompanyView(id, name, createdAt, demo, expiresAt);
    }
}
