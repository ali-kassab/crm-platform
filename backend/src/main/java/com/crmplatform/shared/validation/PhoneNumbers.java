package com.crmplatform.shared.validation;

import com.crmplatform.shared.error.ApiException;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.util.List;
import java.util.Map;

/** Normalises phone numbers to E.164 (VALIDATION_AND_ERROR_HANDLING.md §3, Q-21). */
public final class PhoneNumbers {

    private static final PhoneNumberUtil UTIL = PhoneNumberUtil.getInstance();

    private PhoneNumbers() {
    }

    /**
     * @param raw           user input, e.g. "+1 512-555-0101" or "(512) 555-0101" with a default region
     * @param defaultRegion region used when the number has no country code, e.g. "US"
     * @return E.164 string, or {@code null} for blank input
     */
    public static String normalize(String raw, String defaultRegion, String field) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            PhoneNumber number = UTIL.parse(raw, defaultRegion);
            if (!UTIL.isValidNumber(number)) {
                throw invalid(field);
            }
            return UTIL.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            throw invalid(field);
        }
    }

    private static ApiException invalid(String field) {
        return new ApiException(org.springframework.http.HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "1 field is invalid", Map.of("errors", List.of(Map.of("field", field, "code", "PHONE_INVALID",
                        "message", "must be a valid phone number"))));
    }
}
