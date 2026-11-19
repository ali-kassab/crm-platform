package com.crmplatform.billing.domain;

import com.crmplatform.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

/** Where customers send money (e.g. a Vodafone Cash number or an InstaPay address) and how. */
@Entity
@Table(name = "billing_payment_methods")
public class PaymentMethod extends BaseEntity {

    public enum Kind { VODAFONE_CASH, INSTAPAY, BANK_TRANSFER, OTHER }

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private Kind kind;

    @Column(name = "label_ar", nullable = false, length = 100)
    private String labelAr;

    @Column(name = "label_en", nullable = false, length = 100)
    private String labelEn;

    @Column(nullable = false, length = 150)
    private String account;

    @Column(name = "instructions_ar", length = 2000)
    private String instructionsAr;

    @Column(name = "instructions_en", length = 2000)
    private String instructionsEn;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected PaymentMethod() {
    }

    public PaymentMethod(Kind kind) {
        this.kind = kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }

    public void label(String ar, String en) {
        this.labelAr = ar.trim();
        this.labelEn = en.trim();
    }

    public void setAccount(String account) {
        this.account = account.trim();
    }

    public void instructions(String ar, String en) {
        this.instructionsAr = ar;
        this.instructionsEn = en;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public Kind getKind() {
        return kind;
    }

    public String getLabelAr() {
        return labelAr;
    }

    public String getLabelEn() {
        return labelEn;
    }

    public String getAccount() {
        return account;
    }

    public String getInstructionsAr() {
        return instructionsAr;
    }

    public String getInstructionsEn() {
        return instructionsEn;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
