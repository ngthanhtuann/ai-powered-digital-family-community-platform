package com.familyconnect.modules.genealogy.dto;

import java.util.List;
import java.util.UUID;

/**
 * genealogy.yaml RelationshipResponse.data (UC-GEN-08). path chỉ có id và fullName (BR-GEN-10) vì
 * kết quả còn được module AI dùng làm ngữ cảnh.
 */
public record RelationshipResponse(boolean supported, String relation, String description, UUID commonAncestorId,
                                   Integer stepsFromA, Integer stepsFromB, List<PathPerson> path) {

    public record PathPerson(UUID id, String fullName) {
    }
}
