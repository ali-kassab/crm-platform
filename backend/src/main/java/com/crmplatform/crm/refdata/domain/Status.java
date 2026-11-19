package com.crmplatform.crm.refdata.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "statuses")
@SQLRestriction("deleted_at is null")
public class Status extends Label {

    protected Status() {
    }

    public Status(LabelValues values) {
        update(values);
    }
}
