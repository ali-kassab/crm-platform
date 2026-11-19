package com.crmplatform.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Layering rules inside every module (ADR-0001). */
@AnalyzeClasses(packages = "com.crmplatform", importOptions = ImportOption.DoNotIncludeTests.class)
class LayeringRulesTest {

    @ArchTest
    static final ArchRule controllersDoNotUseRepositoriesDirectly = noClasses()
            .that().haveSimpleNameEndingWith("Controller")
            .should().dependOnClassesThat().haveSimpleNameEndingWith("Repository")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule domainDoesNotDependOnWeb = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web..", "jakarta.servlet..")
            .allowEmptyShould(true);

    /**
     * {@code @ApplicationModuleListener} opens its transaction before the method body runs, so the
     * Hibernate tenant is fixed before the listener could set the event's workspace. Listeners must
     * use {@code TenantWork} instead (tenant isolation, ADR-0004).
     */
    @ArchTest
    static final ArchRule noModuleListenersThatOpenTransactionsBeforeTheTenant = noMethods()
            .should().beAnnotatedWith("org.springframework.modulith.events.ApplicationModuleListener")
            .because("the tenant must be set before a transaction opens; use TenantWork")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule listenersAndJobsDoNotOpenTransactionsBeforeTheTenant = noMethods()
            .that().areAnnotatedWith("org.springframework.transaction.event.TransactionalEventListener")
            .or().areAnnotatedWith("org.springframework.scheduling.annotation.Scheduled")
            .should().beAnnotatedWith("org.springframework.transaction.annotation.Transactional")
            .because("the tenant must be set before a transaction opens; use TenantWork")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule noFieldInjection = noClasses()
            .should().dependOnClassesThat().haveFullyQualifiedName("org.springframework.beans.factory.annotation.Autowired")
            .because("constructor injection is used everywhere")
            .allowEmptyShould(true);
}
