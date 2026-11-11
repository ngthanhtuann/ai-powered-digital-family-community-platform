package com.familyconnect.modules.ai.dto;

import com.familyconnect.modules.genealogy.dto.RelationshipResponse;
import java.util.List;
import java.util.UUID;

public record RelationshipExplanationResponse(boolean found, String explanation, String relation,
                                              List<RelationshipResponse.PathPerson> path) { }
