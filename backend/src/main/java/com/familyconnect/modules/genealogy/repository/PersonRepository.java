package com.familyconnect.modules.genealogy.repository;

import com.familyconnect.modules.genealogy.entity.Person;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PersonRepository extends JpaRepository<Person, UUID> {

    Optional<Person> findByIdAndDeletedAtIsNull(UUID id);

    Optional<Person> findByUserIdAndDeletedAtIsNull(UUID userId);
}
