package com.familyconnect.modules.genealogy.controller;

import com.familyconnect.common.api.ApiResponse;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.BranchInput;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.FamilyInput;
import com.familyconnect.modules.genealogy.dto.GenealogyRequests.PersonInput;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.BranchData;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.FamilyData;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.ListResponse;
import com.familyconnect.modules.genealogy.dto.GenealogyResponses.PersonData;
import com.familyconnect.modules.genealogy.service.BranchService;
import com.familyconnect.modules.genealogy.service.FamilyService;
import com.familyconnect.modules.genealogy.service.PersonService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** genealogy.yaml: UC-GEN-01 (families), UC-GEN-02 (branches), UC-GEN-03 (persons). */
@RestController
@RequestMapping("/api/v1")
public class GenealogyController {

    private final FamilyService familyService;
    private final BranchService branchService;
    private final PersonService personService;

    public GenealogyController(FamilyService familyService, BranchService branchService, PersonService personService) {
        this.familyService = familyService;
        this.branchService = branchService;
        this.personService = personService;
    }

    @GetMapping("/families")
    public ListResponse<FamilyData> listFamilies(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return familyService.list(page, size, sort);
    }

    @PostMapping("/families")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FamilyData> createFamily(@Valid @RequestBody FamilyInput input) {
        return ApiResponse.ok(familyService.create(input));
    }

    @GetMapping("/families/{familyId}")
    public ApiResponse<FamilyData> getFamily(@PathVariable UUID familyId) {
        return ApiResponse.ok(familyService.get(familyId));
    }

    @PutMapping("/families/{familyId}")
    public ApiResponse<FamilyData> updateFamily(@PathVariable UUID familyId, @Valid @RequestBody FamilyInput input) {
        return ApiResponse.ok(familyService.update(familyId, input));
    }

    @DeleteMapping("/families/{familyId}")
    public ApiResponse<Void> deleteFamily(@PathVariable UUID familyId) {
        familyService.delete(familyId);
        return ApiResponse.empty();
    }

    @GetMapping("/families/{familyId}/branches")
    public ListResponse<BranchData> listBranches(@PathVariable UUID familyId,
                                                 @RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return branchService.list(familyId, page, size, sort);
    }

    @PostMapping("/families/{familyId}/branches")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BranchData> createBranch(@PathVariable UUID familyId, @Valid @RequestBody BranchInput input) {
        return ApiResponse.ok(branchService.create(familyId, input));
    }

    @PutMapping("/branches/{branchId}")
    public ApiResponse<BranchData> updateBranch(@PathVariable UUID branchId, @Valid @RequestBody BranchInput input) {
        return ApiResponse.ok(branchService.update(branchId, input));
    }

    @DeleteMapping("/branches/{branchId}")
    public ApiResponse<Void> deleteBranch(@PathVariable UUID branchId) {
        branchService.delete(branchId);
        return ApiResponse.empty();
    }

    @GetMapping("/families/{familyId}/persons")
    public ListResponse<PersonData> listPersons(@PathVariable UUID familyId,
                                                @RequestParam(required = false) String q,
                                                @RequestParam(required = false) UUID branchId,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size,
                                                @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return personService.list(familyId, q, branchId, page, size, sort);
    }

    @PostMapping("/families/{familyId}/persons")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<PersonData> createPerson(@PathVariable UUID familyId, @Valid @RequestBody PersonInput input) {
        return ApiResponse.ok(personService.create(familyId, input));
    }

    @GetMapping("/persons/{personId}")
    public ApiResponse<PersonData> getPerson(@PathVariable UUID personId) {
        return ApiResponse.ok(personService.get(personId));
    }

    @PutMapping("/persons/{personId}")
    public ApiResponse<PersonData> updatePerson(@PathVariable UUID personId, @Valid @RequestBody PersonInput input) {
        return ApiResponse.ok(personService.update(personId, input));
    }

    @DeleteMapping("/persons/{personId}")
    public ApiResponse<Void> deletePerson(@PathVariable UUID personId) {
        personService.delete(personId);
        return ApiResponse.empty();
    }
}
