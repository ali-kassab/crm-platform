package com.crmplatform.crm.refdata.domain;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Repositories for reference data; tenant filtering is applied automatically (ADR-0004). */
public final class RefDataRepositories {

    private RefDataRepositories() {
    }

    public interface CategoryRepository extends JpaRepository<Category, UUID> {

        @Query("select c from Category c where (:q is null or app_search_norm(c.name) like :q escape '\\')")
        Page<Category> search(String q, Pageable pageable);

        @Query("select count(c) > 0 from Category c where lower(c.name) = lower(:name) and (:excludeId is null or c.id <> :excludeId)")
        boolean nameTaken(String name, UUID excludeId);
    }

    public interface StatusRepository extends JpaRepository<Status, UUID> {

        @Query("select s from Status s where (:q is null or app_search_norm(s.name) like :q escape '\\') "
                + "and (:categoryId is null or s.categoryId = :categoryId)")
        Page<Status> search(String q, UUID categoryId, Pageable pageable);

        @Query("select count(s) > 0 from Status s where lower(s.name) = lower(:name) and (:excludeId is null or s.id <> :excludeId)")
        boolean nameTaken(String name, UUID excludeId);

        boolean existsByCategoryId(UUID categoryId);

        @Query("select s from Status s where s.startProcedureId = :procedureId and (:excludeId is null or s.id <> :excludeId)")
        List<Status> boundToStart(UUID procedureId, UUID excludeId);

        @Query("select s from Status s where s.startProcedureId = :procedureId or s.stopProcedureId = :procedureId")
        List<Status> boundTo(UUID procedureId);
    }

    public interface TagRepository extends JpaRepository<Tag, UUID> {

        @Query("select t from Tag t where (:q is null or app_search_norm(t.name) like :q escape '\\') "
                + "and (:categoryId is null or t.categoryId = :categoryId)")
        Page<Tag> search(String q, UUID categoryId, Pageable pageable);

        @Query("select count(t) > 0 from Tag t where lower(t.name) = lower(:name) and (:excludeId is null or t.id <> :excludeId)")
        boolean nameTaken(String name, UUID excludeId);

        boolean existsByCategoryId(UUID categoryId);

        @Query("select t from Tag t where t.startProcedureId = :procedureId and (:excludeId is null or t.id <> :excludeId)")
        List<Tag> boundToStart(UUID procedureId, UUID excludeId);

        @Query("select t from Tag t where t.startProcedureId = :procedureId or t.stopProcedureId = :procedureId")
        List<Tag> boundTo(UUID procedureId);
    }
}
