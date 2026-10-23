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
@Table(name = "heritage_document")
public class HeritageDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 100)
    private String category;

    @Column(name = "media_id", nullable = false)
    private UUID mediaId;

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

    protected HeritageDocument() {
    }

    public HeritageDocument(UUID familyId, String title, String description, String category,
                            UUID mediaId, UUID createdBy) {
        this.familyId = familyId;
        this.title = title;
        this.description = description;
        this.category = category;
        this.mediaId = mediaId;
        this.createdBy = createdBy;
    }

    public void update(String title, String description, boolean updateDescription,
                       String category, boolean updateCategory, UUID actorId) {
        if (title != null) {
            this.title = title;
        }
        if (updateDescription) {
            this.description = description;
        }
        if (updateCategory) {
            this.category = category;
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
    public String getDescription() { return description; }
    public String getCategory() { return category; }
    public UUID getMediaId() { return mediaId; }
    public UUID getCreatedBy() { return createdBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
