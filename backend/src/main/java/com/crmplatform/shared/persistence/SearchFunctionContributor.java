package com.crmplatform.shared.persistence;

import com.crmplatform.shared.web.SearchText;
import org.hibernate.boot.model.FunctionContributions;
import org.hibernate.boot.model.FunctionContributor;
import org.hibernate.type.StandardBasicTypes;

/**
 * Registers the SQL search-normalisation function (Flyway V11) with Hibernate as returning a
 * string, so JPQL ({@code app_search_norm(x) like :q}) and Criteria queries can use it in LIKE.
 * Loaded through {@code META-INF/services/org.hibernate.boot.model.FunctionContributor}.
 */
public class SearchFunctionContributor implements FunctionContributor {

    @Override
    public void contributeFunctions(FunctionContributions contributions) {
        contributions.getFunctionRegistry().registerPattern(SearchText.SQL_FUNCTION,
                SearchText.SQL_FUNCTION + "(?1)",
                contributions.getTypeConfiguration().getBasicTypeRegistry().resolve(StandardBasicTypes.STRING));
    }
}
