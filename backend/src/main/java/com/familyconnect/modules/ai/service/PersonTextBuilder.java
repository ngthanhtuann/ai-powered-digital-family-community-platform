package com.familyconnect.modules.ai.service;

import com.familyconnect.modules.ai.dto.PersonEmbeddingInput;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PersonTextBuilder {

    public String build(PersonEmbeddingInput person) {
        if (person == null || person.fullName() == null
                || person.fullName().isBlank()) {
            throw new IllegalArgumentException(
                    "Person name is required");
        }

        List<String> fields = new ArrayList<>();

        fields.add("Họ tên: " + person.fullName().trim());

        if (person.under16()) {
            if (person.relationship() != null
                    && !person.relationship().isBlank()) {
                fields.add("Quan hệ: "
                        + person.relationship().trim());
            }

            return String.join(". ", fields) + ".";
        }

        if (person.birthYear() != null) {
            fields.add("Năm sinh: " + person.birthYear());
        }

        if (person.relationship() != null
                && !person.relationship().isBlank()) {
            fields.add("Quan hệ: "
                    + person.relationship().trim());
        }

        if (person.occupation() != null
                && !person.occupation().isBlank()) {
            fields.add("Nghề nghiệp: "
                    + person.occupation().trim());
        }

        return String.join(". ", fields) + ".";
    }
}