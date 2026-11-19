package com.crmplatform.identity.domain;

import com.crmplatform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "workspaces")
@SQLRestriction("deleted_at is null")
public class Workspace extends BaseEntity {

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(nullable = false, length = 20)
    private String locale = "en-US";

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone = "UTC";

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Workspace() {
    }

    public Workspace(String name, String slug) {
        this.name = name.trim();
        this.slug = slug;
    }

    /** Lower-case ASCII slug derived from a display name, e.g. "Acme Builders!" → "acme-builders". */
    public static String slugify(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (slug.isEmpty()) {
            slug = "workspace";
        }
        return slug.length() > 80 ? slug.substring(0, 80) : slug;
    }

    public void rename(String name) {
        this.name = name.trim();
    }

    public void delete(Instant at) {
        this.deletedAt = at;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }

    public String getLocale() {
        return locale;
    }

    public String getTimeZone() {
        return timeZone;
    }
}
