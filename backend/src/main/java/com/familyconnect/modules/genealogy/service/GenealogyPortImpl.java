package com.familyconnect.modules.genealogy.service;

import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.api.GenealogyPort;
import com.familyconnect.modules.genealogy.entity.Branch;
import com.familyconnect.modules.genealogy.entity.Family;
import com.familyconnect.modules.genealogy.entity.Person;
import com.familyconnect.modules.genealogy.repository.BranchRepository;
import com.familyconnect.modules.genealogy.repository.FamilyRepository;
import com.familyconnect.modules.genealogy.repository.PersonRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Cài đặt GenealogyPort cho module auth gọi (auth.md mục 6.3b, genealogy.md mục 9). */
@Service
@Transactional(readOnly = true)
public class GenealogyPortImpl implements GenealogyPort {

    private static final int MIN_ACCOUNT_AGE = 16;

    private final FamilyRepository familyRepository;
    private final BranchRepository branchRepository;
    private final PersonRepository personRepository;
    private final Clock clock;

    public GenealogyPortImpl(FamilyRepository familyRepository, BranchRepository branchRepository,
                             PersonRepository personRepository, Clock clock) {
        this.familyRepository = familyRepository;
        this.branchRepository = branchRepository;
        this.personRepository = personRepository;
        this.clock = clock;
    }

    @Override
    public Optional<String> findFamilyName(UUID familyId) {
        return familyRepository.findByIdAndDeletedAtIsNull(familyId).map(Family::getName);
    }

    @Override
    public Optional<String> findBranchName(UUID familyId, UUID branchId) {
        return branchRepository.findByIdAndFamilyIdAndDeletedAtIsNull(branchId, familyId).map(Branch::getName);
    }

    @Override
    public boolean isBranchWithin(UUID familyId, UUID branchId, UUID ancestorBranchId) {
        if (familyId == null || branchId == null || ancestorBranchId == null) {
            return false;
        }
        return branchRepository.isWithin(familyId, branchId, ancestorBranchId);
    }

    @Override
    @Transactional
    public void unlinkAccount(UUID userId) {
        personRepository.findByUserIdAndDeletedAtIsNull(userId).ifPresent(person -> person.setUserId(null));
    }

    @Override
    @Transactional
    public void linkAccount(UUID familyId, UUID personId, UUID userId) {
        Person person = personRepository.findByIdAndDeletedAtIsNull(personId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND_001));
        if (!person.getFamilyId().equals(familyId)) {
            throw new ApiException(ErrorCode.GEN_014); // BR-GEN-19
        }
        if (isUnder16(person)) {
            throw new ApiException(ErrorCode.GEN_012); // BR-GEN-16
        }
        if (person.getUserId() != null) {
            if (person.getUserId().equals(userId)) {
                return;
            }
            throw new ApiException(ErrorCode.CONFLICT_001, "Hồ sơ đã được gắn với tài khoản khác");
        }
        // Một tài khoản chỉ gắn một hồ sơ: gỡ liên kết cũ trước để không vi phạm chỉ mục duy nhất user_id.
        personRepository.findByUserIdAndDeletedAtIsNull(userId).ifPresent(old -> {
            old.setUserId(null);
            personRepository.saveAndFlush(old);
        });
        person.setUserId(userId);
    }

    private boolean isUnder16(Person person) {
        return !person.isDeceased() && person.getBirthDate() != null
                && person.getBirthDate().plusYears(MIN_ACCOUNT_AGE).isAfter(LocalDate.now(clock));
    }
}
