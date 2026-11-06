package com.familyconnect.modules.directory.repository;

import com.familyconnect.modules.directory.entity.ProfessionProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Collection;
import java.util.UUID;

public interface ProfessionProfileRepository extends JpaRepository<ProfessionProfile, UUID> {

    Optional<ProfessionProfile> findByPersonIdAndDeletedAtIsNull(UUID personId);

    List<ProfessionProfile> findAllByPersonIdInAndDeletedAtIsNull(Collection<UUID> personIds);

    @Query("""
            select p.personId from ProfessionProfile p
            where p.familyId = :familyId and p.deletedAt is null
              and lower(p.jobTitle) like lower(concat('%', :job, '%'))
            """)
    List<UUID> findPersonIdsByJobTitle(@Param("familyId") UUID familyId, @Param("job") String job);

    @Query("""
            select p.jobTitle as jobTitle, count(p.id) as count
            from ProfessionProfile p
            where p.personId in :personIds and p.deletedAt is null
            group by p.jobTitle
            order by count(p.id) desc, p.jobTitle asc
            """)
    List<JobTitleCount> countByPersonIds(@Param("personIds") List<UUID> personIds);

    interface JobTitleCount {
        String getJobTitle();
        long getCount();
    }
}
