package com.crmplatform.shared.i18n;

import com.crmplatform.shared.security.CurrentUser;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

/**
 * Picks the response language for a request: an explicit {@code Accept-Language} header first,
 * then the signed-in user's language (carried in the access token: their preference, else the
 * workspace default), then Arabic. Registered as Spring MVC's {@code localeResolver}.
 */
@Component("localeResolver")
public class RequestLanguage implements LocaleResolver {

    public static String of(HttpServletRequest request) {
        return Languages.fromAcceptLanguage(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE))
                .or(() -> CurrentUser.get().map(u -> u.language()).filter(Languages::isSupported))
                .orElse(Languages.DEFAULT);
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return Languages.locale(of(request));
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        throw new UnsupportedOperationException("The language is chosen per request (header, user or workspace)");
    }
}
