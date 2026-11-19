package com.crmplatform.architecture;

import com.crmplatform.CrmPlatformApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/** Verifies module boundaries (ADR-0001) and renders module documentation. */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(CrmPlatformApplication.class);

    @Test
    void modulesRespectTheirBoundaries() {
        modules.verify();
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
