package com.crmplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.modulith.Modulithic;

/**
 * Entry point of the CRM Platform modular monolith.
 *
 * <p>Each direct sub-package is an application module (see docs/adr/0001). {@code shared} contains
 * cross-cutting infrastructure that every module may use.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@Modulithic(sharedModules = "shared")
public class CrmPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrmPlatformApplication.class, args);
    }
}
