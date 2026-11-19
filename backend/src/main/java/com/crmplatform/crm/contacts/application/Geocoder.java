package com.crmplatform.crm.contacts.application;

import com.crmplatform.shared.persistence.Address;
import java.math.BigDecimal;
import java.util.Optional;

/**
 * Geocoding port (Q-17). The default adapter returns nothing; a real provider can be plugged in
 * later. A failure never blocks saving (BR-023).
 */
public interface Geocoder {

    Optional<Coordinates> geocode(Address address);

    record Coordinates(BigDecimal latitude, BigDecimal longitude) {
    }
}
