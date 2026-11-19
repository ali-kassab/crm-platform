package com.crmplatform.identity.security;

import com.crmplatform.shared.i18n.ErrorTexts;
import com.crmplatform.shared.i18n.Languages;
import com.crmplatform.shared.i18n.RequestLanguage;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Writes Problem Details from servlet filters (outside Spring MVC exception handling), localized
 * like every other error response.
 */
@Component
class ProblemWriter {

    private final ObjectMapper objectMapper;
    private final ErrorTexts texts;

    ProblemWriter(ObjectMapper objectMapper, ErrorTexts texts) {
        this.objectMapper = objectMapper;
        this.texts = texts;
    }

    void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String detail)
            throws IOException {
        Locale locale = Languages.locale(RequestLanguage.of(request));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, texts.detail(code, detail, Map.of(), locale));
        problem.setType(URI.create("https://crm-platform.dev/problems/" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(texts.title(status, locale));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("traceId", UUID.randomUUID().toString().replace("-", ""));
        response.setStatus(status.value());
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader("Content-Language", locale.getLanguage());
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
