package com.familyconnect.modules.genealogy.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public class MarriageStatusInput {
    @NotNull(message = "Status không được để trống (MARRIED, DIVORCED, WIDOWED)")
    private String status;

    private LocalDate endDate;

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
