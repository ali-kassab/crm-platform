package com.crmplatform.work.boards.domain;

import com.crmplatform.shared.error.ApiException;
import com.crmplatform.shared.persistence.TenantEntity;
import com.crmplatform.work.Boards.OwnerType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.SQLRestriction;

/**
 * A Kanban board owned by a project or job (BR-070). Every committed change increments the
 * optimistic {@code version}, which doubles as the board version clients sync on (ADR-0006).
 */
@Entity
@Table(name = "boards")
@SQLRestriction("deleted_at is null")
public class Board extends TenantEntity {

    public static final int MAX_COLUMNS = 20;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false, updatable = false, length = 10)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, length = 10)
    private String workflow = "SIMPLE";

    @Column(name = "max_wip")
    private Integer maxWip;

    @Column(name = "last_changed_at", nullable = false)
    private Instant lastChangedAt = Instant.now();

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("position asc")
    private List<BoardColumn> columns = new ArrayList<>();

    protected Board() {
    }

    public Board(OwnerType ownerType, UUID ownerId, String title) {
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.title = title;
    }

    /** Marks the board as changed so its version increments on flush. */
    public void touch(Instant at) {
        this.lastChangedAt = at;
    }

    public BoardColumn addColumn(String title, boolean makeDefault) {
        if (columns.size() >= MAX_COLUMNS) {
            throw ApiException.badRequest("VALIDATION_ERROR", "A board can have at most " + MAX_COLUMNS + " columns");
        }
        BoardColumn column = new BoardColumn(this, title, columns.size() + 1, false);
        columns.add(column);
        if (makeDefault || columns.size() == 1) {
            setDefault(column.getId());
        }
        return column;
    }

    public void renameColumn(UUID columnId, String title) {
        column(columnId).rename(title);
    }

    public void setDefault(UUID columnId) {
        BoardColumn target = column(columnId);
        columns.forEach(c -> c.markDefault(false));
        target.markDefault(true);
    }

    /**
     * Unmarks the current default. Callers flush this before marking another column, because the
     * single-default index is not deferrable and Hibernate may otherwise write the new default first.
     */
    public void clearDefault() {
        columns.forEach(c -> c.markDefault(false));
    }

    /** Moves a column to {@code position} (clamped) and renumbers 1..n (BR-071). */
    public void moveColumn(UUID columnId, int position) {
        BoardColumn column = column(columnId);
        List<BoardColumn> ordered = sorted();
        ordered.remove(column);
        int index = Math.max(0, Math.min(position - 1, ordered.size()));
        ordered.add(index, column);
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).reposition(i + 1);
        }
        columns.clear();
        columns.addAll(ordered);
    }

    public void removeColumn(UUID columnId) {
        if (columns.size() <= 1) {
            throw ApiException.conflict("LAST_COLUMN", "A board needs at least one column");
        }
        BoardColumn column = column(columnId);
        boolean wasDefault = column.isDefault();
        columns.remove(column);
        List<BoardColumn> ordered = sorted();
        for (int i = 0; i < ordered.size(); i++) {
            ordered.get(i).reposition(i + 1);
        }
        if ((wasDefault || ordered.stream().noneMatch(BoardColumn::isDefault)) && !ordered.isEmpty()) {
            ordered.get(0).markDefault(true);
        }
    }

    public BoardColumn column(UUID columnId) {
        return columns.stream().filter(c -> c.getId().equals(columnId)).findFirst()
                .orElseThrow(() -> ApiException.badRequest("COLUMN_NOT_ON_BOARD", "The column does not belong to this board"));
    }

    public boolean hasColumn(UUID columnId) {
        return columns.stream().anyMatch(c -> c.getId().equals(columnId));
    }

    public BoardColumn defaultColumn() {
        return columns.stream().filter(BoardColumn::isDefault).findFirst()
                .orElseGet(() -> sorted().isEmpty() ? null : sorted().get(0));
    }

    private List<BoardColumn> sorted() {
        List<BoardColumn> list = new ArrayList<>(columns);
        list.sort(Comparator.comparingInt(BoardColumn::getPosition));
        return list;
    }

    public OwnerType getOwnerType() {
        return ownerType;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getTitle() {
        return title;
    }

    public String getWorkflow() {
        return workflow;
    }

    public Integer getMaxWip() {
        return maxWip;
    }

    public List<BoardColumn> getColumns() {
        return sorted();
    }
}
