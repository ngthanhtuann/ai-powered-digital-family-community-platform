package com.familyconnect.modules.ai.dto;

import java.util.List;
import java.util.UUID;

/** ai.yaml explainRelationship: relation là kết quả của thuật toán gia phả, explanation do LLM diễn đạt lại. */
public record RelationshipExplanationResponse(boolean found, String explanation, String relation,
                                              List<PathPerson> path) {

    public record PathPerson(UUID id, String fullName) {
    }
}
