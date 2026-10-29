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
}
