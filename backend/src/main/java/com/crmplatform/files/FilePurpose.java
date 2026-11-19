package com.crmplatform.files;

/**
 * Why a file was attached (directive §5F): photos before, during and after execution, inspection
 * evidence, technical reports, warranty and equipment documents. Optional; {@code OTHER} otherwise.
 */
public enum FilePurpose {
    BEFORE, DURING, AFTER, INSPECTION, REPORT, WARRANTY, EQUIPMENT, RECEIPT, OTHER
}
