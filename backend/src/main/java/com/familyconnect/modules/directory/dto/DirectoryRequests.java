package com.familyconnect.modules.directory.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class DirectoryRequests {

    private DirectoryRequests() {
    }

    public record ProfessionProfileRequest(
            @NotBlank @Size(max = 150) String jobTitle,
            @Size(max = 200) String company,
            @Size(max = 200) String workLocation) {
    }

    public record EducationProfileRequest(
            @NotBlank @Size(max = 200) String school,
            @Size(max = 100) String degree,
            @Min(1900) @Max(9999) Integer graduationYear) {
    }
}
