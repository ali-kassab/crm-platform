package com.crmplatform.shared.spi;

import java.util.UUID;

/**
 * Lets lower modules validate automation procedure references without depending on the automation
 * module (which itself depends on them). Implemented by the automation module.
 */
public interface ProcedureDirectory {

    boolean exists(UUID procedureId);
}
