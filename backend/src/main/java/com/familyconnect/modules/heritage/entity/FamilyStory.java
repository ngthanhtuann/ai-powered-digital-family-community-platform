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
@Table(name = "family_story")
public class FamilyStory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "related_person_id")
    private UUID relatedPersonId;

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

    protected FamilyStory() {
    }

    public FamilyStory(UUID familyId, String title, String content, UUID relatedPersonId, UUID createdBy) {
        this.familyId = familyId;
        this.title = title;
        this.content = content;
        this.relatedPersonId = relatedPersonId;
        this.createdBy = createdBy;
    }

    public void update(String title, String content, UUID relatedPersonId, boolean updateRelatedPerson,
                       UUID actorId) {
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        if (updateRelatedPerson) {
            this.relatedPersonId = relatedPersonId;
        }
        this.updatedBy = actorId;
    }

    public void delete(OffsetDateTime at, UUID actorId) {
        this.deletedAt = at;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public String getTitle() { return title; }
    public String getContent() { return content; }
    public UUID getRelatedPersonId() { return relatedPersonId; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
