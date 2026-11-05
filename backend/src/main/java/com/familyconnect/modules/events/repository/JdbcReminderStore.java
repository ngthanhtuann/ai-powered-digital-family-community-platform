package com.familyconnect.modules.events.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Chỉ đọc event + event_participant thuộc module Events; không JOIN bảng auth hay Community. */
@Repository
public class JdbcReminderStore {
    public record Candidate(UUID eventId, UUID familyId, UUID branchId, UUID userId,
                            String eventTitle, OffsetDateTime startAt) {}

    private final NamedParameterJdbcTemplate jdbc;
    public JdbcReminderStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Các mốc đã đến hạn nhưng sự kiện vẫn chưa bắt đầu; bắt kịp sau khi ứng dụng tạm ngừng. */
    public List<Candidate> due(Instant now, int hoursBeforeStart) {
        var args = new MapSqlParameterSource()
            .addValue("now",OffsetDateTime.ofInstant(now,ZoneOffset.UTC))
            .addValue("cutoff",OffsetDateTime.ofInstant(now.plusSeconds(hoursBeforeStart*3600L),ZoneOffset.UTC));
        return jdbc.query("SELECT e.id AS event_id,e.family_id,e.branch_id,p.user_id,e.title,e.start_at " +
            "FROM event e JOIN event_participant p ON p.event_id=e.id " +
            "WHERE e.deleted_at IS NULL AND e.hidden_at IS NULL AND p.deleted_at IS NULL " +
            "AND p.rsvp_status IN ('GOING','MAYBE') AND e.start_at > :now AND e.start_at <= :cutoff",
            args,(r,n) -> new Candidate(r.getObject("event_id",UUID.class),r.getObject("family_id",UUID.class),
                r.getObject("branch_id",UUID.class),r.getObject("user_id",UUID.class),
                r.getString("title"),r.getObject("start_at",OffsetDateTime.class)));
    }
}
