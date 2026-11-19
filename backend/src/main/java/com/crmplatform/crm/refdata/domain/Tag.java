package com.crmplatform.crm.refdata.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "tags")
@SQLRestriction("deleted_at is null")
public class Tag extends Label {

    protected Tag() {
    }

    public Tag(LabelValues values) {
        update(values);
    }
}
