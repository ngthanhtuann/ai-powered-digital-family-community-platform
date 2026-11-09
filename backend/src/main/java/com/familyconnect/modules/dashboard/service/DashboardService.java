package com.familyconnect.modules.dashboard.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.common.security.CurrentUser;
import com.familyconnect.modules.auth.api.EffectiveRole;
import com.familyconnect.modules.auth.api.MembershipQuery;
import com.familyconnect.modules.auth.api.MembershipView;
import com.familyconnect.modules.community.api.CommunityQuery;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.DashboardStatisticsData;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.AgeStatistic;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.GenderStatistic;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.GenerationStatistic;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.LocationStatistic;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.ProfessionStatistic;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.UpcomingEventStatistic;
import com.familyconnect.modules.dashboard.dto.DashboardResponses.WeeklyPostStatistic;
import com.familyconnect.modules.directory.api.DirectoryQuery;
import com.familyconnect.modules.directory.api.DirectoryQuery.ProfessionDetails;
import com.familyconnect.modules.events.api.EventQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DashboardDemographics;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPage;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DirectoryPerson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private static final ZoneId FAMILY_TIME_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final long CACHE_TTL_NANOS = 30_000_000_000L;
    private static final int CACHE_CAPACITY = 256;
    private static final int UPCOMING_EVENT_LIMIT = 5;

    private final GenealogyQuery genealogyQuery;
    private final DirectoryQuery directoryQuery;
    private final CommunityQuery communityQuery;
    private final EventQuery eventQuery;
    private final MembershipQuery membershipQuery;
    private final AuditLogger auditLogger;
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);

    public DashboardService(GenealogyQuery genealogyQuery, DirectoryQuery directoryQuery,
                            CommunityQuery communityQuery, EventQuery eventQuery,
                            MembershipQuery membershipQuery, AuditLogger auditLogger, Clock clock) {
        this.genealogyQuery = genealogyQuery;
        this.directoryQuery = directoryQuery;
        this.communityQuery = communityQuery;
        this.eventQuery = eventQuery;
        this.membershipQuery = membershipQuery;
        this.auditLogger = auditLogger;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public DashboardStatisticsData getStatistics(UUID requestedFamilyId, UUID requestedBranchId) {
        UUID actorId = CurrentUser.require().id();
        MembershipView membership = membershipQuery.getMembership(actorId);
        UUID familyId = resolveFamilyId(actorId, requestedFamilyId, membership);
        UUID branchId = resolveBranchId(familyId, requestedBranchId, membership);
        CacheKey key = new CacheKey(familyId, branchId);
        DashboardStatisticsData cached = getCached(key);
        if (cached != null) {
            return cached;
        }
        DashboardStatisticsData result = loadStatistics(familyId, branchId);
        putCached(key, result);
        return result;
    }

    @Transactional(readOnly = true)
    public byte[] exportMembersCsv(UUID requestedFamilyId, UUID requestedBranchId) {
        UUID actorId = CurrentUser.require().id();
        MembershipView membership = membershipQuery.getMembership(actorId);
        UUID familyId = resolveFamilyId(actorId, requestedFamilyId, membership);
        UUID branchId = resolveBranchId(familyId, requestedBranchId, membership);
        LocalDate today = LocalDate.now(clock.withZone(FAMILY_TIME_ZONE));
        DashboardDemographics scope = genealogyQuery.getDashboardDemographics(familyId, branchId, today);
        Set<UUID> branchIds = Set.copyOf(scope.branchIds());
        List<DirectoryPerson> people = new ArrayList<>();
        long total = 0;
        int page = 0;
        DirectoryPage result;
        do {
            result = genealogyQuery.searchDirectoryPeople(familyId, null, null, null, today, null,
                    false, "fullName", true, page++, 100);
            for (DirectoryPerson person : result.people()) {
                if (branchId == null || person.branchId() != null && branchIds.contains(person.branchId())) {
                    people.add(person);
                }
            }
            total = result.total();
        } while (page * 100L < total && !result.people().isEmpty());

        List<UUID> personIds = people.stream().map(DirectoryPerson::id).toList();
        Set<UUID> minors = people.stream().filter(person -> isMinor(person, today))
                .map(DirectoryPerson::id).collect(Collectors.toSet());
        Map<UUID, ProfessionDetails> professions = directoryQuery.findProfessionDetails(personIds).stream()
                .collect(Collectors.toMap(ProfessionDetails::personId, detail -> detail));
        Map<UUID, String> relationships = genealogyQuery.findDirectoryPersonByUserId(actorId)
                .filter(person -> familyId.equals(person.familyId()))
                .map(person -> genealogyQuery.relationshipLabels(person.id(), personIds))
                .orElseGet(Map::of);

        StringBuilder csv = new StringBuilder(128 + people.size() * 128);
        appendCsvRow(csv, List.of("id", "fullName", "birthYear", "gender", "relationship",
                "generation", "jobTitle", "workLocation"));
        for (DirectoryPerson person : people) {
            boolean minor = minors.contains(person.id());
            ProfessionDetails profession = professions.get(person.id());
            appendCsvRow(csv, List.of(
                    person.id().toString(),
                    person.fullName(),
                    value(person.birthYear()),
                    minor ? "" : value(person.gender()),
                    relationships.getOrDefault(person.id(), ""),
                    minor ? "" : value(person.generation()),
                    minor || profession == null ? "" : value(profession.jobTitle()),
                    minor || profession == null ? "" : value(profession.workLocation())));
        }

        auditLogger.log("MEMBERS_CSV_EXPORTED", "FAMILY", familyId, familyId,
                Map.of("branchScope", branchId == null ? "ALL_FAMILY" : branchId.toString(),
                        "rowCount", people.size()));
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        byte[] bom = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] resultBytes = new byte[bom.length + content.length];
        System.arraycopy(bom, 0, resultBytes, 0, bom.length);
        System.arraycopy(content, 0, resultBytes, bom.length, content.length);
        return resultBytes;
    }

    private UUID resolveFamilyId(UUID actorId, UUID requestedFamilyId, MembershipView membership) {
        if (membership.role() != EffectiveRole.SYSTEM_ADMIN) {
            membershipQuery.requireVerified(actorId);
            if (membership.role() != EffectiveRole.BRANCH_ADMIN) {
                throw new ApiException(ErrorCode.PERM_001);
            }
        }

        UUID familyId;
        if (membership.role() == EffectiveRole.SYSTEM_ADMIN) {
            if (requestedFamilyId == null) {
                throw ApiException.invalidField("familyId", "SYSTEM_ADMIN phải truyền familyId");
            }
            familyId = requestedFamilyId;
        } else {
            familyId = membership.familyId();
            if (familyId == null || requestedFamilyId != null && !requestedFamilyId.equals(familyId)) {
                throw new ApiException(ErrorCode.PERM_001);
            }
        }
        membershipQuery.requireFamilyAccess(actorId, familyId);
        if (!genealogyQuery.familyExists(familyId)) {
            throw new ApiException(ErrorCode.NOT_FOUND_001);
        }
        return familyId;
    }

    private UUID resolveBranchId(UUID familyId, UUID requestedBranchId, MembershipView membership) {
        UUID branchId = requestedBranchId != null ? requestedBranchId
                : membership.role() == EffectiveRole.BRANCH_ADMIN ? membership.branchId() : null;
        if (branchId == null) {
            return null;
        }
        if (!genealogyQuery.isBranchWithin(familyId, branchId, branchId)
                || membership.role() == EffectiveRole.BRANCH_ADMIN && membership.branchId() != null
                && !genealogyQuery.isBranchWithin(familyId, branchId, membership.branchId())) {
            throw new ApiException(ErrorCode.PERM_001);
        }
        return branchId;
    }

    private DashboardStatisticsData loadStatistics(UUID familyId, UUID branchId) {
        OffsetDateTime now = OffsetDateTime.now(clock.withZone(FAMILY_TIME_ZONE));
        LocalDate today = now.toLocalDate();
        DashboardDemographics demographics = genealogyQuery.getDashboardDemographics(familyId, branchId, today);
        List<ProfessionStatistic> professions = directoryQuery
                .getProfessionStatistics(demographics.professionPersonIds()).stream()
                .map(item -> new ProfessionStatistic(item.jobTitle(), item.count()))
                .toList();

        LocalDate currentWeek = today.with(DayOfWeek.MONDAY);
        LocalDate firstWeek = currentWeek.minusWeeks(7);
        OffsetDateTime from = firstWeek.atStartOfDay(FAMILY_TIME_ZONE).toOffsetDateTime();
        OffsetDateTime to = currentWeek.plusWeeks(1).atStartOfDay(FAMILY_TIME_ZONE).toOffsetDateTime();
        Map<LocalDate, Long> postCounts = communityQuery.getWeeklyPostCounts(familyId, branchId == null,
                        demographics.branchIds(), from, to).stream()
                .collect(java.util.stream.Collectors.toMap(
                        CommunityQuery.WeeklyPostCount::weekStart, CommunityQuery.WeeklyPostCount::postCount));
        List<WeeklyPostStatistic> weeklyPosts = firstWeek.datesUntil(currentWeek.plusWeeks(1))
                .filter(date -> date.getDayOfWeek() == DayOfWeek.MONDAY)
                .map(date -> new WeeklyPostStatistic(date.atStartOfDay(FAMILY_TIME_ZONE).toOffsetDateTime(),
                        postCounts.getOrDefault(date, 0L)))
                .toList();

        List<UpcomingEventStatistic> upcomingEvents = eventQuery.getUpcomingEvents(familyId, branchId == null,
                        demographics.branchIds(), now, UPCOMING_EVENT_LIMIT).stream()
                .map(event -> new UpcomingEventStatistic(event.id(), event.title(), event.startAt()))
                .toList();

        List<GenerationStatistic> generations = demographics.generationStats().stream()
                .map(item -> new GenerationStatistic(item.generation(), item.count()))
                .toList();
        List<AgeStatistic> ages = demographics.ageStats().stream()
                .map(item -> new AgeStatistic(item.ageRange(), item.count()))
                .toList();
        List<LocationStatistic> locations = demographics.locationStats().stream()
                .map(item -> new LocationStatistic(item.location(), item.count()))
                .toList();
        return new DashboardStatisticsData(demographics.totalMembers(), generations, ages,
                new GenderStatistic(demographics.maleCount(), demographics.femaleCount(), demographics.otherCount()),
                professions, locations, weeklyPosts, upcomingEvents);
    }

    private static boolean isMinor(DirectoryPerson person, LocalDate today) {
        if (person.deceased()) {
            return false;
        }
        if (person.birthDate() != null) {
            return person.birthDate().plusYears(16).isAfter(today);
        }
        return person.birthYear() != null && person.birthYear() > today.getYear() - 16;
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static void appendCsvRow(StringBuilder csv, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                csv.append(',');
            }
            String value = values.get(index) == null ? "" : values.get(index);
            String leading = value.stripLeading();
            if (!leading.isEmpty() && "=+-@".indexOf(leading.charAt(0)) >= 0) {
                value = "'" + value;
            }
            csv.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        csv.append("\r\n");
    }

    private DashboardStatisticsData getCached(CacheKey key) {
        synchronized (cache) {
            CacheEntry entry = cache.get(key);
            if (entry == null) {
                return null;
            }
            if (System.nanoTime() >= entry.expiresAtNanos()) {
                cache.remove(key);
                return null;
            }
            return entry.data();
        }
    }

    private void putCached(CacheKey key, DashboardStatisticsData data) {
        synchronized (cache) {
            cache.put(key, new CacheEntry(System.nanoTime() + CACHE_TTL_NANOS, data));
            while (cache.size() > CACHE_CAPACITY) {
                cache.remove(cache.keySet().iterator().next());
            }
        }
    }

    private record CacheKey(UUID familyId, UUID branchId) {
    }

    private record CacheEntry(long expiresAtNanos, DashboardStatisticsData data) {
    }
}
