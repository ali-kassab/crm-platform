package com.crmplatform.identity.domain;

import com.crmplatform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.text.Normalizer;
import java.time.Instant;
import java.util.Locale;
import org.hibernate.annotations.SQLRestriction;

/**
 * A tenant. Language, locale (date/number formats), currency and time zone are independent settings
 * (D11); new workspaces default to Arabic, {@code ar-EG}, EGP and {@code Africa/Cairo}.
 */
@Entity
@Table(name = "workspaces")
@SQLRestriction("deleted_at is null")
public class Workspace extends BaseEntity {

    /** The three target sectors; selects onboarding defaults, never separate behaviour. */
    public enum Sector { FIT_OUT, SECURITY_SYSTEMS, MAINTENANCE }

    public static final String DEFAULT_LANGUAGE = "ar";
    public static final String DEFAULT_LOCALE = "ar-EG";
    public static final String DEFAULT_CURRENCY = "EGP";
    public static final String DEFAULT_TIME_ZONE = "Africa/Cairo";

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false, length = 100)
    private String slug;

    @Column(name = "default_language", nullable = false, length = 5)
    private String defaultLanguage = DEFAULT_LANGUAGE;

    @Column(nullable = false, length = 20)
    private String locale = DEFAULT_LOCALE;

    @Column(nullable = false, length = 3)
    private String currency = DEFAULT_CURRENCY;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone = DEFAULT_TIME_ZONE;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Sector sector;

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

    /** Values are validated by the caller (supported language, IANA zone, ISO 4217 code). */
    public void configure(String defaultLanguage, String locale, String currency, String timeZone, Sector sector) {
        this.defaultLanguage = defaultLanguage;
        this.locale = locale;
        this.currency = currency;
        this.timeZone = timeZone;
        this.sector = sector;
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

    public String getDefaultLanguage() {
        return defaultLanguage;
    }

    public String getLocale() {
        return locale;
    }

    public String getCurrency() {
        return currency;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public Sector getSector() {
        return sector;
    }
}
