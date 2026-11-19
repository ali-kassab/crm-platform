package com.crmplatform.billing.domain;

import com.crmplatform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/** A sellable plan (platform-level). Limits: {@code null} = unlimited. Managed by the platform admin. */
@Entity
@Table(name = "billing_plans")
public class Plan extends BaseEntity {

    @Column(nullable = false, length = 30, updatable = false)
    private String code;

    @Column(name = "name_ar", nullable = false, length = 100)
    private String nameAr;

    @Column(name = "name_en", nullable = false, length = 100)
    private String nameEn;

    @Column(name = "description_ar", length = 1000)
    private String descriptionAr;

    @Column(name = "description_en", length = 1000)
    private String descriptionEn;

    @Column(name = "price_monthly", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceMonthly;

    @Column(name = "price_yearly", nullable = false, precision = 19, scale = 2)
    private BigDecimal priceYearly;

    @Column(nullable = false, length = 3)
    private String currency = "EGP";

    @Column(name = "max_users")
    private Integer maxUsers;

    @Column(name = "max_active_projects")
    private Integer maxActiveProjects;

    @Column(name = "max_jobs_per_month")
    private Integer maxJobsPerMonth;

    @Column(name = "max_storage_mb")
    private Integer maxStorageMb;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Plan() {
    }

    public Plan(String code) {
        this.code = code.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public void name(String ar, String en) {
        this.nameAr = ar.trim();
        this.nameEn = en.trim();
    }

    public void describe(String ar, String en) {
        this.descriptionAr = ar;
        this.descriptionEn = en;
    }

    public void price(BigDecimal monthly, BigDecimal yearly, String currency) {
        this.priceMonthly = monthly;
        this.priceYearly = yearly;
        this.currency = currency == null ? "EGP" : currency;
    }

    public void limits(Integer users, Integer activeProjects, Integer jobsPerMonth, Integer storageMb) {
        this.maxUsers = users;
        this.maxActiveProjects = activeProjects;
        this.maxJobsPerMonth = jobsPerMonth;
        this.maxStorageMb = storageMb;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public BigDecimal priceFor(BillingPeriod period) {
        return period == BillingPeriod.YEARLY ? priceYearly : priceMonthly;
    }

    public String getCode() {
        return code;
    }

    public String getNameAr() {
        return nameAr;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getDescriptionAr() {
        return descriptionAr;
    }

    public String getDescriptionEn() {
        return descriptionEn;
    }

    public BigDecimal getPriceMonthly() {
        return priceMonthly;
    }

    public BigDecimal getPriceYearly() {
        return priceYearly;
    }

    public String getCurrency() {
        return currency;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public Integer getMaxActiveProjects() {
        return maxActiveProjects;
    }

    public Integer getMaxJobsPerMonth() {
        return maxJobsPerMonth;
    }

    public Integer getMaxStorageMb() {
        return maxStorageMb;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
