package com.familyconnect.modules.genealogy.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.UUID;

public class MarriageInput {
    @NotNull(message = "Person 1 ID không được để trống")
    private UUID person1Id;

    @NotNull(message = "Person 2 ID không được để trống")
    private UUID person2Id;

    private LocalDate startDate;

    public UUID getPerson1Id() { return person1Id; }
    public void setPerson1Id(UUID person1Id) { this.person1Id = person1Id; }

    public UUID getPerson2Id() { return person2Id; }
    public void setPerson2Id(UUID person2Id) { this.person2Id = person2Id; }

    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
}
