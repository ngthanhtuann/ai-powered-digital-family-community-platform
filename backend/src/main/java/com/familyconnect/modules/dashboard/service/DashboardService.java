package com.familyconnect.modules.dashboard.service;

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
import com.familyconnect.modules.events.api.EventQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery;
import com.familyconnect.modules.genealogy.api.GenealogyQuery.DashboardDemographics;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final Clock clock;
    private final Map<CacheKey, CacheEntry> cache = new LinkedHashMap<>(16, 0.75f, true);

    public DashboardService(GenealogyQuery genealogyQuery, DirectoryQuery directoryQuery,
                            CommunityQuery communityQuery, EventQuery eventQuery,
                            MembershipQuery membershipQuery, Clock clock) {
        this.genealogyQuery = genealogyQuery;
        this.directoryQuery = directoryQuery;
        this.communityQuery = communityQuery;
        this.eventQuery = eventQuery;
        this.membershipQuery = membershipQuery;
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
