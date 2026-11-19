package com.crmplatform.shared.web;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Expression;
import java.util.Locale;

/**
 * Arabic-aware search normalisation, identical to the SQL function {@code app_search_norm} (V11):
 * lower-case, drop tashkeel (U+064B–U+065F, U+0670) and tatweel (U+0640), and unify letter
 * variants أ/إ/آ/ٱ → ا, ى → ي, ة → ه. So «أحمد» matches «احمد» and «مدرسة» matches «مدرسه».
 * Applied to both the query text and the searched column.
 */
public final class SearchText {

    public static final String SQL_FUNCTION = "app_search_norm";

    private SearchText() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if ((ch >= 'ً' && ch <= 'ٟ') || ch == 'ٰ' || ch == 'ـ') {
                continue;
            }
            sb.append(switch (ch) {
                case 'أ', 'إ', 'آ', 'ٱ' -> 'ا'; // أ إ آ ٱ → ا
                case 'ى' -> 'ي'; // ى → ي
                case 'ة' -> 'ه'; // ة → ه
                default -> ch;
            });
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    /** {@code app_search_norm(column)} for Criteria queries; compare it with {@link Pagination#likePattern}. */
    public static Expression<String> column(CriteriaBuilder cb, Expression<String> column) {
        return cb.function(SQL_FUNCTION, String.class, column);
    }
}
