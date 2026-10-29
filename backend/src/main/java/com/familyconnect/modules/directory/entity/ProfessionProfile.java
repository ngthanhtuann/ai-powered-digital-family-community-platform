package com.familyconnect.modules.directory.entity;

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
@Table(name = "profession_profile")
public class ProfessionProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "job_title", nullable = false, length = 150)
    private String jobTitle;

    @Column(length = 200)
    private String company;

    @Column(name = "work_location", length = 200)
    private String workLocation;

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

    protected ProfessionProfile() {
    }

    public ProfessionProfile(UUID familyId, UUID personId, String jobTitle, String company,
                             String workLocation, UUID actorId) {
        this.familyId = familyId;
        this.personId = personId;
        this.jobTitle = jobTitle;
        this.company = company;
        this.workLocation = workLocation;
        this.createdBy = actorId;
    }

    public void update(String jobTitle, String company, String workLocation, UUID actorId) {
        this.jobTitle = jobTitle;
        this.company = company;
        this.workLocation = workLocation;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getPersonId() { return personId; }
    public String getJobTitle() { return jobTitle; }
    public String getCompany() { return company; }
    public String getWorkLocation() { return workLocation; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
