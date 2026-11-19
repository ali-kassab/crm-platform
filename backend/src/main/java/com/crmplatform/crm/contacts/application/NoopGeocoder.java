package com.crmplatform.crm.contacts.application;

import com.crmplatform.shared.persistence.Address;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** Default geocoder: no external calls; coordinates stay empty unless entered manually. A real adapter is registered as {@code @Primary}. */
@Component
class NoopGeocoder implements Geocoder {

    @Override
    public Optional<Coordinates> geocode(Address address) {
        return Optional.empty();
    }
}
