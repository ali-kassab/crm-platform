package com.crmplatform.shared.i18n;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Localizes client-facing error text while keeping the machine-readable {@code code} unchanged.
 *
 * <p>Business code keeps writing plain English messages next to a stable code. For Arabic the text
 * is looked up, in order: an exact translation of the English message
 * ({@code i18n/error-texts_ar.tsv}), then a per-code message ({@code error.<CODE>} in
 * {@code i18n/messages_ar.properties}), then a generic message. English responses keep the
 * original message.
 */
@Component
public class ErrorTexts {

    private final MessageSource messages;
    private final Map<String, String> arabic;

    public ErrorTexts(MessageSource messages) {
        this.messages = messages;
        this.arabic = load("i18n/error-texts_ar.tsv");
    }

    public String detail(String code, String english, Map<String, Object> properties, Locale locale) {
        if (isEnglish(locale)) {
            return english;
        }
        String exact = english == null ? null : arabic.get(english);
        if (exact != null) {
            return exact;
        }
        if ("NOT_FOUND".equals(code) && properties != null && properties.get("resource") instanceof String resource) {
            String name = messages.getMessage("resource." + resource, null, resource, locale);
            return messages.getMessage("error.NOT_FOUND", new Object[] {name}, locale);
        }
        String byCode = messages.getMessage("error." + code, null, null, locale);
        return byCode != null ? byCode : messages.getMessage("error.GENERIC", null, locale);
    }

    public String title(HttpStatus status, Locale locale) {
        return messages.getMessage("title." + status.name(), null, status.getReasonPhrase(), locale);
    }

    public String validationTitle(Locale locale) {
        return messages.getMessage("title.VALIDATION", null, "Validation failed", locale);
    }

    public String fieldsInvalid(int count, Locale locale) {
        return messages.getMessage("error.VALIDATION_ERROR.fields", new Object[] {count}, locale);
    }

    /** A field-level message: English keeps the validator's text; Arabic uses the field code. */
    public String field(String code, String english, Locale locale) {
        if (isEnglish(locale)) {
            return english;
        }
        String exact = english == null ? null : arabic.get(english);
        if (exact != null) {
            return exact;
        }
        return messages.getMessage("field." + code, null, messages.getMessage("field.INVALID", null, locale),
                locale);
    }

    /** Localizes the {@code message} of each {@code errors[]} entry (entries carry a {@code code}). */
    public List<Map<String, Object>> fields(List<?> errors, Locale locale) {
        return errors.stream().map(e -> {
            Map<String, Object> copy = new HashMap<>();
            if (e instanceof Map<?, ?> m) {
                m.forEach((k, v) -> copy.put(String.valueOf(k), v));
                Object code = copy.get("code");
                Object message = copy.get("message");
                copy.put("message", field(code == null ? "INVALID" : code.toString(),
                        message == null ? null : message.toString(), locale));
            }
            return copy;
        }).toList();
    }

    public Map<String, String> arabicTranslations() {
        return Map.copyOf(arabic);
    }

    private static boolean isEnglish(Locale locale) {
        return locale != null && Languages.ENGLISH.equals(locale.getLanguage());
    }

    private static Map<String, String> load(String path) {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(path).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                int tab = line.indexOf('\t');
                if (tab > 0) {
                    map.put(line.substring(0, tab), line.substring(tab + 1).trim());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + path, e);
        }
        return map;
    }
}
