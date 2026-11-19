package com.crmplatform.shared.mail;

import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

/** Renders HTML email templates from {@code templates/email/*.html}. */
@Component
public class EmailTemplates {

    private final TemplateEngine engine;

    public EmailTemplates(TemplateEngine engine) {
        this.engine = engine;
    }

    public String render(String template, Map<String, Object> variables) {
        Context context = new Context(Locale.US, variables);
        return engine.process("email/" + template, context);
    }
}
