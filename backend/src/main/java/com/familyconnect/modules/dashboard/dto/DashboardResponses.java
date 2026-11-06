package com.familyconnect.modules.dashboard.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class DashboardResponses {

    private DashboardResponses() {
    }

    public record DashboardStatisticsData(long totalMembers, List<GenerationStatistic> generationStats,
                                          List<AgeStatistic> ageStats, GenderStatistic genderStats,
                                          List<ProfessionStatistic> professionStats,
                                          List<LocationStatistic> locationStats, List<WeeklyPostStatistic> weeklyPosts,
                                          List<UpcomingEventStatistic> upcomingEvents) {
        public DashboardStatisticsData {
            generationStats = List.copyOf(generationStats);
            ageStats = List.copyOf(ageStats);
            professionStats = List.copyOf(professionStats);
            locationStats = List.copyOf(locationStats);
            weeklyPosts = List.copyOf(weeklyPosts);
            upcomingEvents = List.copyOf(upcomingEvents);
        }
    }

    public record GenerationStatistic(int generation, long count) {
    }

    public record AgeStatistic(String ageRange, long count) {
    }

    public record GenderStatistic(long MALE, long FEMALE, long OTHER) {
    }

    public record ProfessionStatistic(String jobTitle, long count) {
    }

    public record LocationStatistic(String location, long count) {
    }

    public record WeeklyPostStatistic(OffsetDateTime weekStart, long postCount) {
    }

    public record UpcomingEventStatistic(UUID id, String title, OffsetDateTime startAt) {
    }
}
