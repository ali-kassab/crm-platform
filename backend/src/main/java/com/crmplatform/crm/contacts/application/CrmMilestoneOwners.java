package com.crmplatform.crm.contacts.application;

import com.crmplatform.crm.contacts.domain.ContactRepository;
import com.crmplatform.crm.locations.domain.LocationRepository;
import com.crmplatform.milestones.MilestoneOwner;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Lets the milestones module verify contact and location owners. */
@Configuration(proxyBeanMethods = false)
class CrmMilestoneOwners {

    @Bean
    MilestoneOwner contactMilestoneOwner(ContactRepository contacts) {
        return new MilestoneOwner() {
            @Override
            public OwnerType type() {
                return OwnerType.CONTACT;
            }

            @Override
            public boolean exists(UUID id) {
                return contacts.existsById(id);
            }
        };
    }

    @Bean
    MilestoneOwner locationMilestoneOwner(LocationRepository locations) {
        return new MilestoneOwner() {
            @Override
            public OwnerType type() {
                return OwnerType.LOCATION;
            }

            @Override
            public boolean exists(UUID id) {
                return locations.existsById(id);
            }
        };
    }
}
