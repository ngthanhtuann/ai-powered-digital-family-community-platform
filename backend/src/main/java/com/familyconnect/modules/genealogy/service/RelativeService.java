package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.MarriageInput;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.ParentInput;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.ParentKind;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.ParentRole;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.PersonInput;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.RelativeInput;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.PersonData;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * BR-GEN-26 (SCRUM-143): tạo người thân mới và nối quan hệ trong một giao dịch, dùng lại kiểm tra của tạo hồ sơ (UC-GEN-03)
 * và nối quan hệ (UC-GEN-04, 05); bước nào lỗi thì cả giao dịch hoàn tác, không để lại hồ sơ chưa nối.
 */
@Service
public class RelativeService {

    private final PersonRepository personRepository;
    private final PersonService personService;
    private final RelationshipService relationshipService;

    public RelativeService(PersonRepository personRepository, PersonService personService, RelationshipService relationshipService) {
        this.personRepository = personRepository;
        this.personService = personService;
        this.relationshipService = relationshipService;
    }

    @Transactional
    public PersonData addRelative(UUID personId, RelativeInput input) {
        Person anchor = personRepository.findByIdAndDeletedAtIsNull(personId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        PersonInput person = input.person().branchId() != null ? input.person() : withBranch(input.person(), anchor.getBranchId());
        ParentKind kind = input.kind() == null ? ParentKind.BIOLOGICAL : input.kind();
        // Kiểm tra vai trò trước khi tạo để lỗi giới tính không tốn một lần ghi.
        ParentRole role = switch (input.relation()) {
            case CHILD -> roleOf(anchor.getGender(), input.parentRole());
            case PARENT -> roleOf(person.gender().name(), input.parentRole());
            case SPOUSE -> null;
        };
        PersonData created = personService.create(anchor.getFamilyId(), person);
        switch (input.relation()) {
            case CHILD -> relationshipService.addParent(created.id(), new ParentInput(anchor.getId(), role, kind));
            case PARENT -> relationshipService.addParent(anchor.getId(), new ParentInput(created.id(), role, kind));
            case SPOUSE -> relationshipService.addMarriage(new MarriageInput(anchor.getId(), created.id(), input.startDate()));
        }
        return personService.get(created.id());
    }

    private static ParentRole roleOf(String gender, ParentRole explicit) {
        if ("MALE".equals(gender)) {
            return ParentRole.FATHER;
        }
        if ("FEMALE".equals(gender)) {
            return ParentRole.MOTHER;
        }
        if (explicit == null) {
            throw ApiException.invalidField("parentRole", "Chọn người này là cha hay mẹ");
        }
        return explicit;
    }

    private static PersonInput withBranch(PersonInput p, UUID branchId) {
        return new PersonInput(p.fullName(), p.gender(), p.birthDate(), p.birthYear(), p.birthplace(), p.currentProvince(),
                p.currentAddress(), branchId, p.isDeceased(), p.deathDate(), p.burialPlace(), p.avatarMediaId(), p.userId(), p.note());
    }
}
