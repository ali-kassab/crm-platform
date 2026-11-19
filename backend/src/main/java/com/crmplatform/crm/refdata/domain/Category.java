package com.crmplatform.crm.refdata.domain;

import com.crmplatform.shared.persistence.TenantEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "categories")
@SQLRestriction("deleted_at is null")
public class Category extends TenantEntity {

    @Column(nullable = false, length = 50)
    private String name;

    @Column(length = 500)
    private String description;

    protected Category() {
    }

    public Category(String name, String description) {
        update(name, description);
    }

    public void update(String name, String description) {
        this.name = name.trim();
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }
}
