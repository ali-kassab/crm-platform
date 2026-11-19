package com.crmplatform.shared.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

/**
 * Arabic is the primary language, so every client-facing error must have Arabic text. Scans the
 * production sources: each literal message passed to {@code ApiException} needs an exact Arabic
 * translation, and each error code needs an Arabic fallback (used for messages built at runtime).
 */
class ErrorTextsCoverageTest {

    private static final Pattern FACTORY = Pattern.compile(
            "(?:badRequest|conflict|unauthorized|forbidden)\\(\\s*(?:\"([A-Z_]+)\",\\s*)?((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)\\)");
    private static final Pattern CONSTRUCTOR = Pattern.compile(
            "new ApiException\\(\\s*HttpStatus\\.[A-Z_]+,\\s*\"([A-Z_]+)\",\\s*((?:\"(?:[^\"\\\\]|\\\\.)*\"\\s*\\+?\\s*)+)");
    private static final Pattern CODE_ONLY = Pattern.compile(
            "(?:badRequest|conflict|unauthorized)\\(\\s*\"([A-Z_]+)\"|new ApiException\\(\\s*HttpStatus\\.[A-Z_]+,\\s*\"([A-Z_]+)\"");
    private static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    void everyLiteralErrorMessageHasAnArabicTranslation() throws IOException {
        Map<String, String> arabic = new ErrorTexts(new StaticMessageSource()).arabicTranslations();
        Set<String> missing = new TreeSet<>();
        Set<String> checked = new TreeSet<>();
        for (String source : sources()) {
            for (Pattern p : new Pattern[] {FACTORY, CONSTRUCTOR}) {
                Matcher m = p.matcher(source);
                while (m.find()) {
                    String message = join(m.group(2));
                    if (!message.isEmpty()) {
                        checked.add(message);
                        if (!arabic.containsKey(message)) {
                            missing.add(message);
                        }
                    }
                }
            }
        }
        assertThat(checked).as("the scanner must actually find the messages").hasSizeGreaterThan(90);
        assertThat(missing).as("add these to src/main/resources/i18n/error-texts_ar.tsv").isEmpty();
    }

    @Test
    void everyErrorCodeHasAnArabicFallback() throws IOException {
        Properties ar = new Properties();
        try (var in = new InputStreamReader(getClass().getResourceAsStream("/i18n/messages_ar.properties"),
                StandardCharsets.UTF_8)) {
            ar.load(in);
        }
        Set<String> missing = new TreeSet<>();
        for (String source : sources()) {
            Matcher m = CODE_ONLY.matcher(source);
            while (m.find()) {
                String code = m.group(1) != null ? m.group(1) : m.group(2);
                if (!ar.containsKey("error." + code)) {
                    missing.add(code);
                }
            }
        }
        assertThat(missing).as("add error.<CODE> to src/main/resources/i18n/messages_ar.properties").isEmpty();
    }

    private static String join(String concatenation) {
        StringBuilder sb = new StringBuilder();
        Matcher m = LITERAL.matcher(concatenation);
        while (m.find()) {
            sb.append(m.group(1));
        }
        return sb.toString();
    }

    private static Set<String> sources() throws IOException {
        Set<String> sources = new java.util.LinkedHashSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                sources.add(Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }
}
