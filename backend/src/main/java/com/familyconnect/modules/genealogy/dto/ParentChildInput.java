package com.familyconnect.modules.genealogy.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public class ParentChildInput {
    @NotNull(message = "Parent ID không được để trống")
    private UUID parentId;

    @NotNull(message = "Parent Role không được để trống (FATHER, MOTHER)")
    private String parentRole;

    private String kind = "BIOLOGICAL";

    public UUID getParentId() { return parentId; }
    public void setParentId(UUID parentId) { this.parentId = parentId; }

    public String getParentRole() { return parentRole; }
    public void setParentRole(String parentRole) { this.parentRole = parentRole; }

    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
}
