package com.crmplatform.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/** Records that a one-time platform action happened; the primary key makes it happen only once. */
@Entity
@Table(name = "platform_markers")
public class PlatformMarker implements Persistable<String> {

    public static final String ADMIN_BOOTSTRAPPED = "PLATFORM_ADMIN_BOOTSTRAPPED";

    @Id
    @Column(length = 60)
    private String marker;

    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> details;

    @Transient
    private boolean isNew = true;

    protected PlatformMarker() {
    }

    public PlatformMarker(String marker, Instant recordedAt, Map<String, Object> details) {
        this.marker = marker;
        this.recordedAt = recordedAt;
        this.details = Map.copyOf(details);
    }

    @Override
    public String getId() {
        return marker;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @jakarta.persistence.PostLoad
    @jakarta.persistence.PostPersist
    void loaded() {
        this.isNew = false;
    }
}
