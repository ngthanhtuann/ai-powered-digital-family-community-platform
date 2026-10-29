package com.familyconnect.modules.events.repository;

import com.familyconnect.modules.events.dto.EventDto.RsvpCounts;
import com.familyconnect.modules.events.dto.EventDto.RsvpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class JdbcEventStore implements EventStore {
    private static final String EVENT_COLUMNS = "id, family_id, branch_id, title, description, " +
        "start_at, end_at, location, created_by, created_at, updated_at";
    private static final String PARTICIPANT_COLUMNS = "id, event_id, user_id, rsvp_status, created_at, updated_at";
    private static UUID id(ResultSet r, String field) throws SQLException { return r.getObject(field, UUID.class); }
    private static OffsetDateTime at(ResultSet r, String field) throws SQLException {
        return r.getObject(field, OffsetDateTime.class);
    }
    private static final RowMapper<Event> EVENT_MAPPER = (r, n) ->
        new Event(id(r,"id"),id(r,"family_id"),id(r,"branch_id"),r.getString("title"),
            r.getString("description"),at(r,"start_at"),at(r,"end_at"),r.getString("location"),
            id(r,"created_by"),at(r,"created_at"),at(r,"updated_at"));
    private static final RowMapper<Participant> PARTICIPANT_MAPPER = (r,n) ->
        new Participant(id(r,"id"),id(r,"event_id"),id(r,"user_id"),
            RsvpStatus.valueOf(r.getString("rsvp_status")),at(r,"created_at"),at(r,"updated_at"));

    private final NamedParameterJdbcTemplate jdbc;
    public JdbcEventStore(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    private static MapSqlParameterSource p(Object... fields) {
        var args = new MapSqlParameterSource();
        for (int i=0; i<fields.length; i+=2) args.addValue((String)fields[i], fields[i+1]);
        return args;
    }

    @Override public Event insertEvent(UUID family, UUID branch, String title, String description,
                                       OffsetDateTime start, OffsetDateTime end, String location, UUID actor) {
        return jdbc.queryForObject("INSERT INTO event(family_id,branch_id,title,description,start_at,end_at," +
            "location,created_by) VALUES (:family,:branch,:title,:desc,:start,:end,:location,:actor) RETURNING " +
            EVENT_COLUMNS, p("family",family,"branch",branch,"title",title,"desc",description,
            "start",start,"end",end,"location",location,"actor",actor), EVENT_MAPPER);
    }

    @Override public Optional<Event> findEvent(UUID eventId) {
        return jdbc.query("SELECT " + EVENT_COLUMNS + " FROM event WHERE id=:id AND deleted_at IS NULL " +
            "AND hidden_at IS NULL", p("id",eventId), EVENT_MAPPER).stream().findFirst();
    }

    @Override public List<UUID> eventBranchIds(UUID family) {
        return jdbc.queryForList("SELECT DISTINCT branch_id FROM event WHERE family_id=:family " +
            "AND branch_id IS NOT NULL AND deleted_at IS NULL AND hidden_at IS NULL",
            p("family",family),UUID.class);
    }

    @Override public Page<Event> listEvents(UUID family, boolean allBranches, List<UUID> readableBranches,
                                            UUID branch, OffsetDateTime from, OffsetDateTime to,
                                            int page, int size, String order) {
        var args = p("family",family,"branch",branch,"from",from,"to",to,
                     "limit",size,"offset",(long)page*size);
        var where = new StringBuilder(" FROM event WHERE family_id=:family AND deleted_at IS NULL " +
                                      "AND hidden_at IS NULL");
        if (!allBranches) {
            if (readableBranches.isEmpty()) where.append(" AND branch_id IS NULL");
            else { where.append(" AND (branch_id IS NULL OR branch_id IN (:visible))");
                   args.addValue("visible",readableBranches); }
        }
        if (branch != null) where.append(" AND branch_id=:branch");
        if (from != null) where.append(" AND start_at >= :from");
        if (to != null) where.append(" AND start_at <= :to");
        Long count = jdbc.queryForObject("SELECT COUNT(*)" + where,args,Long.class);
        var items = jdbc.query("SELECT " + EVENT_COLUMNS + where + " ORDER BY " + order +
            " LIMIT :limit OFFSET :offset",args,EVENT_MAPPER);
        return new Page<>(items,count == null ? 0 : count);
    }

    @Override public Event updateEvent(UUID id, UUID branch, String title, String description,
                                       OffsetDateTime start, OffsetDateTime end, String location, UUID actor) {
        return jdbc.queryForObject("UPDATE event SET branch_id=:branch,title=:title,description=:desc," +
            "start_at=:start,end_at=:end,location=:location,updated_by=:actor,updated_at=now() " +
            "WHERE id=:id AND deleted_at IS NULL AND hidden_at IS NULL RETURNING " + EVENT_COLUMNS,
            p("id",id,"branch",branch,"title",title,"desc",description,"start",start,
              "end",end,"location",location,"actor",actor),EVENT_MAPPER);
    }

    @Override public void deleteEvent(UUID id, UUID actor) {
        jdbc.update("UPDATE event SET deleted_at=now(),updated_at=now(),updated_by=:actor WHERE id=:id " +
            "AND deleted_at IS NULL",p("id",id,"actor",actor));
    }

    @Override public Participant upsertRsvp(UUID eventId, UUID userId, RsvpStatus status, UUID actor) {
        return jdbc.queryForObject("INSERT INTO event_participant(event_id,user_id,rsvp_status,created_by) " +
            "VALUES (:event,:user,:status,:actor) ON CONFLICT (event_id,user_id) WHERE deleted_at IS NULL " +
            "DO UPDATE SET rsvp_status=EXCLUDED.rsvp_status,updated_by=:actor,updated_at=now() " +
            "RETURNING " + PARTICIPANT_COLUMNS,
            p("event",eventId,"user",userId,"status",status.name(),"actor",actor),PARTICIPANT_MAPPER);
    }

    @Override public Participant insertParticipant(UUID eventId, UUID userId, RsvpStatus status, UUID actor) {
        return jdbc.queryForObject("INSERT INTO event_participant(event_id,user_id,rsvp_status,created_by) " +
            "VALUES (:event,:user,:status,:actor) RETURNING " + PARTICIPANT_COLUMNS,
            p("event",eventId,"user",userId,"status",status.name(),"actor",actor),PARTICIPANT_MAPPER);
    }

    @Override public Optional<Participant> findParticipant(UUID id) {
        return jdbc.query("SELECT " + PARTICIPANT_COLUMNS + " FROM event_participant " +
            "WHERE id=:id AND deleted_at IS NULL",p("id",id),PARTICIPANT_MAPPER).stream().findFirst();
    }

    @Override public Page<Participant> participants(UUID eventId, RsvpStatus status,
                                                     int page, int size, String order) {
        var args = p("event",eventId,"status",status == null ? null : status.name(),
                     "limit",size,"offset",(long)page*size);
        String where = " FROM event_participant WHERE event_id=:event AND deleted_at IS NULL" +
            (status == null ? "" : " AND rsvp_status=:status");
        Long total = jdbc.queryForObject("SELECT COUNT(*)" + where,args,Long.class);
        var items = jdbc.query("SELECT " + PARTICIPANT_COLUMNS + where + " ORDER BY " + order +
            " LIMIT :limit OFFSET :offset",args,PARTICIPANT_MAPPER);
        return new Page<>(items,total == null ? 0 : total);
    }

    @Override public void deleteParticipant(UUID id, UUID actor) {
        jdbc.update("UPDATE event_participant SET deleted_at=now(),updated_at=now(),updated_by=:actor " +
            "WHERE id=:id AND deleted_at IS NULL",p("id",id,"actor",actor));
    }

    @Override public Map<UUID,RsvpCounts> counts(List<UUID> eventIds) {
        Map<UUID,long[]> rows = new HashMap<>();
        eventIds.forEach(id -> rows.put(id,new long[3]));
        if (!eventIds.isEmpty()) {
            jdbc.query("SELECT event_id,rsvp_status,COUNT(*) AS total FROM event_participant " +
                "WHERE event_id IN (:ids) AND deleted_at IS NULL GROUP BY event_id,rsvp_status",
                p("ids",eventIds),rs -> {
                    long[] values = rows.get(id(rs,"event_id"));
                    if (values != null) values[RsvpStatus.valueOf(rs.getString("rsvp_status")).ordinal()] = rs.getLong("total");
                });
        }
        Map<UUID,RsvpCounts> result = new HashMap<>();
        rows.forEach((id,counts) -> result.put(id,new RsvpCounts(counts[0],counts[1],counts[2])));
        return result;
    }

    @Override public Map<UUID,RsvpStatus> myStatuses(List<UUID> eventIds, UUID user) {
        var result = new HashMap<UUID,RsvpStatus>();
        if (!eventIds.isEmpty()) jdbc.query("SELECT event_id,rsvp_status FROM event_participant " +
            "WHERE event_id IN (:ids) AND user_id=:user AND deleted_at IS NULL",
            p("ids",eventIds,"user",user),rs -> {
                result.put(id(rs,"event_id"),RsvpStatus.valueOf(rs.getString("rsvp_status")));
            });
        return result;
    }
}
