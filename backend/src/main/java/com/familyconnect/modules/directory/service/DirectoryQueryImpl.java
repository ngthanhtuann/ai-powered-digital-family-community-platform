package com.familyconnect.modules.directory.service;

import com.familyconnect.modules.directory.api.DirectoryQuery;
import com.familyconnect.modules.directory.api.DirectoryQuery.ProfessionStatistic;
import com.familyconnect.modules.directory.entity.ProfessionProfile;
import com.familyconnect.modules.directory.repository.ProfessionProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DirectoryQueryImpl implements DirectoryQuery {

    private final ProfessionProfileRepository professionRepository;

    public DirectoryQueryImpl(ProfessionProfileRepository professionRepository) {
        this.professionRepository = professionRepository;
    }

    @Override
    public Optional<String> findJobTitle(UUID personId) {
        return professionRepository.findByPersonIdAndDeletedAtIsNull(personId).map(ProfessionProfile::getJobTitle);
    }

    @Override
    public List<ProfessionStatistic> getProfessionStatistics(List<UUID> personIds) {
        if (personIds.isEmpty()) {
            return List.of();
        }
        return professionRepository.countByPersonIds(personIds).stream()
                .map(item -> new ProfessionStatistic(item.getJobTitle(), item.getCount()))
                .toList();
    }
}
