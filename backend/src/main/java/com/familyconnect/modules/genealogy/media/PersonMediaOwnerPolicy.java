package com.familyconnect.modules.genealogy.media;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.community.api.MediaOwnerPolicy;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import com.familyconnect.modules.genealogy.service.GenealogyAccess;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** PERSON: kiểm tra quyền đọc/quản lý bằng quy tắc hiện có của module Gia phả. */
@Component
public class PersonMediaOwnerPolicy implements MediaOwnerPolicy {
    private final PersonRepository persons;
    private final GenealogyAccess access;

    public PersonMediaOwnerPolicy(PersonRepository persons, GenealogyAccess access) {
        this.persons = persons;
        this.access = access;
    }

    @Override public String ownerType() { return "PERSON"; }

    private Person person(UUID id, UUID familyId) {
        Person person = persons.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        if (familyId != null && !familyId.equals(person.getFamilyId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        return person;
    }

    @Override public UUID requireUploadFamily(UUID ownerId) {
        Person person = person(ownerId, null);
        var viewer = access.requireRead(person.getFamilyId());
        access.requireManage(viewer, person.getFamilyId(), person.getBranchId());
        return person.getFamilyId();
    }

    @Override public void requireRead(UUID ownerId, UUID familyId) {
        person(ownerId, familyId);
        access.requireRead(familyId);
    }
}
