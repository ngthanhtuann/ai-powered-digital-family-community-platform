package com.familyconnect.modules.genealogy.service;

import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Bản tạm đến khi SCRUM-37 làm bảng person: đọc ánh xạ personId=familyId từ HERITAGE_GENEALOGY_PEOPLE.
 * Không cấu hình thì mọi người liên quan đều không tồn tại (404).
 */
@Component
public class ConfiguredGenealogyQuery implements GenealogyQuery {

    private final Map<UUID, UUID> people;

    public ConfiguredGenealogyQuery(
            @Value("${app.heritage.genealogy.people:}") String personFamilyMappings) {
        this.people = parseMappings(personFamilyMappings);
    }

    @Override
    public Optional<UUID> findPersonFamilyId(UUID personId) {
        return Optional.ofNullable(people.get(personId));
    }

    private static Map<UUID, UUID> parseMappings(String mappings) {
        Map<UUID, UUID> people = new HashMap<>();
        if (mappings.isBlank()) {
            return Map.of();
        }

        for (String mapping : mappings.split(";")) {
            String[] ids = mapping.trim().split("=", -1);
            if (ids.length != 2 || ids[0].isBlank() || ids[1].isBlank()) {
                throw new IllegalArgumentException(
                        "HERITAGE_GENEALOGY_PEOPLE entries must use personUUID=familyUUID");
            }
            UUID personId = UUID.fromString(ids[0].trim());
            UUID familyId = UUID.fromString(ids[1].trim());
            if (people.putIfAbsent(personId, familyId) != null) {
                throw new IllegalArgumentException("HERITAGE_GENEALOGY_PEOPLE contains a duplicate person ID");
            }
        }
        return Map.copyOf(people);
    }
}
