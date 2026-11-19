package com.crmplatform.billing.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Commercial settings. All values are provisional (directive) and can change without code changes.
 *
 * @param enforcementEnabled  false turns off read-only mode and plan limits (feature flag)
 * @param trialDays           automatic trial on sign-up (D6)
 * @param graceDays           full access after the period ends, with warnings (D5)
 * @param trialPlanCode       plan whose limits apply during the trial
 * @param reminderBefore      "your subscription ends soon" notice
 */
@ConfigurationProperties("app.billing")
public record BillingProperties(
        @DefaultValue("true") boolean enforcementEnabled,
        @DefaultValue("14") int trialDays,
        @DefaultValue("7") int graceDays,
        @DefaultValue("TEAM") String trialPlanCode,
        @DefaultValue("3d") Duration reminderBefore) {
}
