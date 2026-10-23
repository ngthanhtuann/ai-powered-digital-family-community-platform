package com.familyconnect.modules.genealogy.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Thành viên trong gia phả (bảng person, data-dictionary.md). */
@Entity
@Table(name = "person")
public class Person {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(nullable = false, length = 10)
    private String gender;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "birth_year")
    private Short birthYear;

    @Column(length = 255)
    private String birthplace;

    @Column(name = "current_province", length = 100)
    private String currentProvince;

    @Column(name = "current_address", length = 255)
    private String currentAddress;

    @Column(name = "is_deceased", nullable = false)
    private boolean deceased;

    @Column(name = "death_date")
    private LocalDate deathDate;

    @Column(name = "burial_place", length = 255)
    private String burialPlace;

    @Column(name = "avatar_media_id")
    private UUID avatarMediaId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(columnDefinition = "text")
    private String note;

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

    public Person() {
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getFamilyId() { return familyId; }
    public void setFamilyId(UUID familyId) { this.familyId = familyId; }
    public UUID getBranchId() { return branchId; }
    public void setBranchId(UUID branchId) { this.branchId = branchId; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public LocalDate getBirthDate() { return birthDate; }
    public void setBirthDate(LocalDate birthDate) { this.birthDate = birthDate; }
    public Short getBirthYear() { return birthYear; }
    public void setBirthYear(Short birthYear) { this.birthYear = birthYear; }
    public String getBirthplace() { return birthplace; }
    public void setBirthplace(String birthplace) { this.birthplace = birthplace; }
    public String getCurrentProvince() { return currentProvince; }
    public void setCurrentProvince(String currentProvince) { this.currentProvince = currentProvince; }
    public String getCurrentAddress() { return currentAddress; }
    public void setCurrentAddress(String currentAddress) { this.currentAddress = currentAddress; }
    public boolean isDeceased() { return deceased; }
    public void setDeceased(boolean deceased) { this.deceased = deceased; }
    public LocalDate getDeathDate() { return deathDate; }
    public void setDeathDate(LocalDate deathDate) { this.deathDate = deathDate; }
    public String getBurialPlace() { return burialPlace; }
    public void setBurialPlace(String burialPlace) { this.burialPlace = burialPlace; }
    public UUID getAvatarMediaId() { return avatarMediaId; }
    public void setAvatarMediaId(UUID avatarMediaId) { this.avatarMediaId = avatarMediaId; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public UUID getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(UUID updatedBy) { this.updatedBy = updatedBy; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public OffsetDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(OffsetDateTime deletedAt) { this.deletedAt = deletedAt; }
}
