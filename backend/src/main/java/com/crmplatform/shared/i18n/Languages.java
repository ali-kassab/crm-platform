package com.crmplatform.shared.i18n;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Supported user-interface languages. Arabic is the default (Arabic-first product); English is the
 * second language. This is only about fixed system text — user-entered data is never translated.
 */
public final class Languages {

    public static final String ARABIC = "ar";
    public static final String ENGLISH = "en";
    public static final String DEFAULT = ARABIC;
    public static final List<String> SUPPORTED = List.of(ARABIC, ENGLISH);

    private Languages() {
    }

    /** {@code "ar-EG"}, {@code "AR"}, {@code "en_US"} → {@code ar}/{@code en}; unsupported → empty. */
    public static Optional<String> normalize(String tag) {
        if (tag == null || tag.isBlank()) {
            return Optional.empty();
        }
        String language = Locale.forLanguageTag(tag.trim().replace('_', '-')).getLanguage();
        return SUPPORTED.contains(language) ? Optional.of(language) : Optional.empty();
    }

    /** First supported language of an {@code Accept-Language} header, honouring q-weights. */
    public static Optional<String> fromAcceptLanguage(String header) {
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        try {
            for (Locale.LanguageRange range : Locale.LanguageRange.parse(header)) {
                Optional<String> language = normalize(range.getRange());
                if (language.isPresent()) {
                    return language;
                }
            }
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        return Optional.empty();
    }

    public static boolean isSupported(String language) {
        return language != null && SUPPORTED.contains(language);
    }

    public static Locale locale(String language) {
        return Locale.forLanguageTag(isSupported(language) ? language : DEFAULT);
    }
}
