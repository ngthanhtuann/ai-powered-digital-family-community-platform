package com.familyconnect.modules.genealogy.service;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConfiguredGenealogyQueryTest {

    private static final UUID PERSON_ID = UUID.fromString("50000000-0000-0000-0000-000000000005");
    private static final UUID FAMILY_ID = UUID.fromString("20000000-0000-0000-0000-000000000002");

    @Test
    void configuredPersonResolvesToFamily() {
        var query = new ConfiguredGenealogyQuery(PERSON_ID + "=" + FAMILY_ID);

        assertThat(query.findPersonFamilyId(PERSON_ID)).contains(FAMILY_ID);
        assertThat(query.findPersonFamilyId(UUID.randomUUID())).isEmpty();
    }

    @Test
    void emptyConfigurationHasNoKnownPeople() {
        var query = new ConfiguredGenealogyQuery("");

        assertThat(query.findPersonFamilyId(PERSON_ID)).isEmpty();
    }

    @Test
    void malformedConfigurationFailsFast() {
        assertThatThrownBy(() -> new ConfiguredGenealogyQuery("not-a-person-id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("personUUID=familyUUID");
    }

    @Test
    void duplicatePersonConfigurationFailsFast() {
        String mappings = PERSON_ID + "=" + FAMILY_ID + ";" + PERSON_ID + "=" + FAMILY_ID;

        assertThatThrownBy(() -> new ConfiguredGenealogyQuery(mappings))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate person ID");
    }
}
