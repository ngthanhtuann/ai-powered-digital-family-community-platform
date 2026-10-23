package com.familyconnect.modules.genealogy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Chi họ (bảng branch, data-dictionary.md); parent_branch_id NULL là chi gốc. */
@Entity
@Table(name = "branch")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "parent_branch_id")
    private UUID parentBranchId;

    @Column(name = "origin_person_id")
    private UUID originPersonId;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public Branch() {
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public void setFamilyId(UUID familyId) { this.familyId = familyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public UUID getParentBranchId() { return parentBranchId; }
    public void setParentBranchId(UUID parentBranchId) { this.parentBranchId = parentBranchId; }
    public UUID getOriginPersonId() { return originPersonId; }
    public void setOriginPersonId(UUID originPersonId) { this.originPersonId = originPersonId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
