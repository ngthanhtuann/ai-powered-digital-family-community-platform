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
@Table(name = "education_profile")
public class EducationProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(nullable = false, length = 200)
    private String school;

    @Column(length = 100)
    private String degree;

    @Column(name = "graduation_year")
    private Integer graduationYear;

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

    protected EducationProfile() {
    }

    public EducationProfile(UUID familyId, UUID personId, String school, String degree,
                            Integer graduationYear, UUID actorId) {
        this.familyId = familyId;
        this.personId = personId;
        this.school = school;
        this.degree = degree;
        this.graduationYear = graduationYear;
        this.createdBy = actorId;
    }

    public void update(String school, String degree, Integer graduationYear, UUID actorId) {
        this.school = school;
        this.degree = degree;
        this.graduationYear = graduationYear;
        this.updatedBy = actorId;
    }

    public UUID getId() { return id; }
    public UUID getFamilyId() { return familyId; }
    public UUID getPersonId() { return personId; }
    public String getSchool() { return school; }
    public String getDegree() { return degree; }
    public Integer getGraduationYear() { return graduationYear; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
