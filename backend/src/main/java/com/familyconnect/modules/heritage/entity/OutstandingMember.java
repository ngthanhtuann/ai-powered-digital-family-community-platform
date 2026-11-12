package com.familyconnect.modules.heritage.entity;

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

@Entity
@Table(name = "outstanding_member")
public class OutstandingMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(nullable = false, columnDefinition = "text")
    private String achievement;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

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

    protected OutstandingMember() {
    }

    public OutstandingMember(UUID familyId, UUID personId, String achievement, int displayOrder, UUID createdBy) {
        this.familyId = familyId;
        this.personId = personId;
        this.achievement = achievement;
        this.displayOrder = displayOrder;
        this.createdBy = createdBy;
    }

    public void update(String achievement, Integer displayOrder, UUID actorId) {
        if (achievement != null) {
            this.achievement = achievement;
        }
        if (displayOrder != null) {
            this.displayOrder = displayOrder;
        }
        this.updatedBy = actorId;
    }

    public void delete(OffsetDateTime at, UUID actorId) {
        this.deletedAt = at;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getPersonId() { return personId; }
    public String getAchievement() { return achievement; }
    public int getDisplayOrder() { return displayOrder; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
