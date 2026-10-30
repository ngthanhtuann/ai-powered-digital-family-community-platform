package com.familyconnect.modules.genealogy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Request của genealogy.yaml: FamilyInput, BranchInput, PersonInput. */
public final class GenealogyRequests {

    private GenealogyRequests() {
    }

    public enum Gender { MALE, FEMALE, OTHER }

    public enum ParentRole { FATHER, MOTHER }

    public enum ParentKind { BIOLOGICAL, ADOPTED, STEP }

    public enum MarriageStatus { MARRIED, DIVORCED, WIDOWED }

    public record FamilyInput(
            @NotBlank(message = "Tên gia đình không được để trống")
            @Size(max = 150, message = "Tên gia đình tối đa 150 ký tự") String name,
            String description) {
    }

    public record BranchInput(
            @NotBlank(message = "Tên chi không được để trống")
            @Size(max = 150, message = "Tên chi tối đa 150 ký tự") String name,
            UUID parentBranchId,
            UUID originPersonId,
            String description) {
    }

    public record PersonInput(
            @NotBlank(message = "Họ tên không được để trống")
            @Size(max = 150, message = "Họ tên tối đa 150 ký tự") String fullName,
            @NotNull(message = "Chưa chọn giới tính") Gender gender,
            LocalDate birthDate,
            Integer birthYear,
            @Size(max = 255, message = "Nơi sinh tối đa 255 ký tự") String birthplace,
            @Size(max = 100, message = "Tỉnh/thành tối đa 100 ký tự") String currentProvince,
            @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự") String currentAddress,
            UUID branchId,
            Boolean isDeceased,
            LocalDate deathDate,
            @Size(max = 255, message = "Nơi an táng tối đa 255 ký tự") String burialPlace,
            UUID avatarMediaId,
            UUID userId,
            String note) {

        public boolean deceased() {
            return Boolean.TRUE.equals(isDeceased);
        }
    }

    /** POST /persons/{personId}/parents (genealogy.yaml ParentInput); kind mặc định BIOLOGICAL. */
    public record ParentInput(
            @NotNull(message = "Chưa chọn cha/mẹ") UUID parentId,
            @NotNull(message = "Chưa chọn vai trò cha hoặc mẹ") ParentRole parentRole,
            ParentKind kind) {
    }

    /** POST /marriages (MarriageInput). */
    public record MarriageInput(
            @NotNull(message = "Chưa chọn người thứ nhất") UUID person1Id,
            @NotNull(message = "Chưa chọn người thứ hai") UUID person2Id,
            LocalDate startDate) {
    }

    /** PUT /marriages/{marriageId} (MarriageUpdate). */
    public record MarriageUpdate(
            @NotNull(message = "Chưa chọn trạng thái hôn nhân") MarriageStatus status,
            LocalDate startDate,
            LocalDate endDate) {
    }
}

