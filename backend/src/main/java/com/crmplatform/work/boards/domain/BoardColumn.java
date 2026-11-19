package com.crmplatform.work.boards.domain;

import com.crmplatform.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "board_columns")
public class BoardColumn {

    @Id
    private UUID id = UuidV7.generate();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false, updatable = false)
    private Board board;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(nullable = false)
    private int position;

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    protected BoardColumn() {
    }

    BoardColumn(Board board, String title, int position, boolean isDefault) {
        this.board = board;
        this.title = title.trim();
        this.position = position;
        this.isDefault = isDefault;
    }

    void rename(String title) {
        this.title = title.trim();
    }

    void reposition(int position) {
        this.position = position;
    }

    void markDefault(boolean value) {
        this.isDefault = value;
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getPosition() {
        return position;
    }

    public boolean isDefault() {
        return isDefault;
    }
}
