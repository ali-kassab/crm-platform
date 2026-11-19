package com.crmplatform.shared.mail;

import com.crmplatform.shared.i18n.Languages;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/**
 * Renders HTML email templates from {@code templates/email/*.html} in the recipient's language:
 * {@code <name>_ar.html} (right-to-left) for Arabic, {@code <name>.html} for English. Subjects come
 * from {@code i18n/messages*.properties} ({@code email.<name>.subject}).
 */
@Component
public class EmailTemplates {

    private final TemplateEngine engine;
    private final MessageSource messages;

    public EmailTemplates(TemplateEngine engine, MessageSource messages) {
        this.engine = engine;
        this.messages = messages;
    }

    public String render(String template, String language, Map<String, Object> variables) {
        Locale locale = Languages.locale(language);
        String name = Languages.ARABIC.equals(locale.getLanguage()) ? template + "_ar" : template;
        return engine.process("email/" + name, new Context(locale, variables));
    }

    public String subject(String template, String language, Object... args) {
        return messages.getMessage("email." + template + ".subject", args, Languages.locale(language));
    }

    public String text(String key, String language) {
        return messages.getMessage(key, null, key, Languages.locale(language));
    }
}
