package com.crmplatform.work.boards.domain;

import com.crmplatform.work.Boards.OwnerType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public final class BoardRepositories {

    private BoardRepositories() {
    }

    public interface BoardRepository extends JpaRepository<Board, UUID> {

        Optional<Board> findByOwnerTypeAndOwnerId(OwnerType ownerType, UUID ownerId);

        /** Serialises concurrent board mutations (ADR-0006). */
        @Lock(LockModeType.PESSIMISTIC_WRITE)
        @Query("select b from Board b where b.id = :id")
        Optional<Board> findByIdForUpdate(UUID id);
    }

    public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

        List<Task> findByBoardIdOrderByPositionAsc(UUID boardId);

        List<Task> findByColumnIdOrderByPositionAsc(UUID columnId);

        long countByColumnId(UUID columnId);

        List<Task> findByProjectId(UUID projectId);

        List<Task> findByBoardIdIn(Collection<UUID> boardIds);

        @Modifying(flushAutomatically = true, clearAutomatically = false)
        @Query("update Task t set t.position = t.position + :delta where t.columnId = :columnId "
                + "and t.position >= :from and t.position <= :to and t.id <> :exclude")
        int shift(UUID columnId, int from, int to, int delta, UUID exclude);
    }

    public interface TaskCommentRepository extends JpaRepository<TaskComment, UUID> {

        List<TaskComment> findByTaskIdOrderByCreatedAtAsc(UUID taskId);

        @Query("select c.taskId, count(c) from TaskComment c where c.taskId in :taskIds group by c.taskId")
        List<Object[]> countByTask(Collection<UUID> taskIds);
    }

    public interface BoardTemplateRepository extends JpaRepository<BoardTemplate, UUID> {

        Optional<BoardTemplate> findFirstByIsDefaultTrue();

        List<BoardTemplate> findByIsDefaultTrue();

        @Query("select t from BoardTemplate t where (:q is null or lower(t.title) like :q escape '\\')")
        org.springframework.data.domain.Page<BoardTemplate> search(String q, org.springframework.data.domain.Pageable p);
    }
}
