package com.crmplatform.billing.domain;

import java.time.Instant;
import java.time.ZoneOffset;

public enum BillingPeriod {
    MONTHLY, YEARLY;

    public Instant after(Instant start) {
        var at = start.atOffset(ZoneOffset.UTC);
        return (this == YEARLY ? at.plusYears(1) : at.plusMonths(1)).toInstant();
    }
}
