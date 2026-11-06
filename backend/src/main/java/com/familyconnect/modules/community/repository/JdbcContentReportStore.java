package com.familyconnect.modules.community.repository;

import com.familyconnect.modules.auth.api.ContentModerationPort.ContentReportView;
import com.familyconnect.modules.auth.api.ContentModerationPort.ContentType;
import com.familyconnect.modules.auth.api.ContentModerationPort.ReportStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Bảng content_report và cột hidden_at của nội dung (BR-COM-12). Sự kiện cùng module Community/Events. */
@Repository
public class JdbcContentReportStore {

    /** Nội dung bị báo cáo: phạm vi để kiểm quyền xem, chủ nội dung và văn bản. */
    public record Target(UUID familyId, UUID branchId, UUID ownerId, String text) {
    }

    public record Page(List<ContentReportView> items, long total) {
    }

    /** Mỗi người báo cáo có một bản ghi để nhận kết quả riêng (hàng đợi quản trị vẫn gộp). */
    public record Reporter(UUID reportId, UUID userId) {}

    private static final String COLUMNS = "id, target_type, target_id, family_id, reporter_id, reason, "
            + "content_snippet, status, created_at, updated_at";

    private static ContentReportView view(ResultSet rs, int reportCount) throws SQLException {
        return new ContentReportView(rs.getObject("id", UUID.class), ContentType.valueOf(rs.getString("target_type")),
                rs.getObject("target_id", UUID.class), rs.getObject("family_id", UUID.class),
                rs.getObject("reporter_id", UUID.class), rs.getString("reason"), rs.getString("content_snippet"),
                ReportStatus.valueOf(rs.getString("status")), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class), null, reportCount);
    }

    private static final RowMapper<ContentReportView> SINGLE = (rs, n) -> view(rs, 1);
    private static final RowMapper<ContentReportView> GROUPED = (rs, n) -> view(rs, rs.getInt("report_count"));

    private static final RowMapper<Target> TARGET = (rs, n) -> new Target(rs.getObject("family_id", UUID.class),
            rs.getObject("branch_id", UUID.class), rs.getObject("created_by", UUID.class), rs.getString("text"));

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcContentReportStore(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Nội dung theo loại. visibleOnly: chưa xóa, chưa ẩn (để báo cáo); ngược lại chỉ cần chưa xóa (để khôi phục).
     * Văn bản sự kiện ghép như EventAccess.eventChanged để chỉ mục AI giống hệt.
     */
    public Optional<Target> findTarget(ContentType type, UUID id, boolean visibleOnly) {
        String hidden = visibleOnly ? " AND hidden_at IS NULL" : "";
        String sql = switch (type) {
            case POST -> "SELECT family_id, branch_id, created_by, content AS text FROM post "
                    + "WHERE id=:id AND deleted_at IS NULL" + hidden;
            case COMMENT -> "SELECT p.family_id, p.branch_id, c.created_by, c.content AS text FROM comment c "
                    + "JOIN post p ON p.id = c.post_id WHERE c.id=:id AND c.deleted_at IS NULL"
                    + (visibleOnly ? " AND c.hidden_at IS NULL" : "")
                    + " AND p.deleted_at IS NULL AND p.hidden_at IS NULL";
            case EVENT -> "SELECT family_id, branch_id, created_by, "
                    + "title || ' ' || COALESCE(description, '') AS text FROM event "
                    + "WHERE id=:id AND deleted_at IS NULL" + hidden;
            case ANNOUNCEMENT -> "SELECT family_id, branch_id, created_by, "
                    + "title || ' ' || content AS text FROM announcement "
                    + "WHERE id=:id AND deleted_at IS NULL" + hidden;
        };
        if (sql == null) {
            return Optional.empty();
        }
        return jdbc.query(sql, new MapSqlParameterSource("id", id), TARGET).stream().findFirst();
    }

    /**
     * Số giây phải chờ trước khi người này được báo cáo tiếp (0 là được ngay): đã có ít nhất {@code limit} báo cáo
     * trong 1 giờ qua thì chờ tới khi báo cáo cũ nhất trong nhóm đó ra khỏi cửa sổ (conventions.md mục 7.1).
     */
    public long reportWaitSeconds(UUID reporterId, int limit) {
        List<Long> wait = jdbc.queryForList("SELECT CEIL(EXTRACT(EPOCH FROM (MIN(created_at) + interval '1 hour' - now()))) "
                        + "FROM (SELECT created_at FROM content_report WHERE reporter_id=:reporter "
                        + "AND created_at > now() - interval '1 hour' ORDER BY created_at DESC LIMIT :limit) r "
                        + "HAVING COUNT(*) >= :limit",
                new MapSqlParameterSource().addValue("reporter", reporterId).addValue("limit", limit), Long.class);
        // HAVING không khớp (chưa đủ limit) thì không có dòng nào.
        return wait.isEmpty() || wait.get(0) == null ? 0 : Math.max(wait.get(0), 1);
    }

    public boolean exists(ContentType type, UUID targetId, UUID reporterId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM content_report "
                        + "WHERE target_type=:type AND target_id=:target AND reporter_id=:reporter AND deleted_at IS NULL)",
                new MapSqlParameterSource().addValue("type", type.name()).addValue("target", targetId)
                        .addValue("reporter", reporterId), Boolean.class));
    }

    public ContentReportView insert(UUID familyId, ContentType type, UUID targetId, UUID reporterId, String reason,
                                    String snippet) {
        return jdbc.queryForObject("INSERT INTO content_report(family_id, target_type, target_id, reporter_id, reason, "
                        + "content_snippet, created_by) VALUES (:family, :type, :target, :reporter, :reason, :snippet, "
                        + ":reporter) RETURNING " + COLUMNS,
                new MapSqlParameterSource().addValue("family", familyId).addValue("type", type.name())
                        .addValue("target", targetId).addValue("reporter", reporterId).addValue("reason", reason)
                        .addValue("snippet", snippet), SINGLE);
    }

    /** Hàng đợi gộp theo nội dung (BR-AUTH-15): mỗi nội dung là báo cáo sớm nhất, kèm số lượt báo cáo. */
    public Page find(ReportStatus status, ContentType type, boolean ascending, int limit, long offset) {
        var args = new MapSqlParameterSource().addValue("status", status.name())
                .addValue("type", type == null ? null : type.name()).addValue("limit", limit).addValue("offset", offset);
        String where = " FROM content_report WHERE deleted_at IS NULL AND status=:status"
                + (type == null ? "" : " AND target_type=:type");
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT DISTINCT target_type, target_id" + where + ") t",
                args, Long.class);
        String dir = ascending ? "ASC" : "DESC";
        List<ContentReportView> items = jdbc.query("SELECT * FROM (SELECT DISTINCT ON (target_type, target_id) "
                + COLUMNS + ", COUNT(*) OVER (PARTITION BY target_type, target_id) AS report_count" + where
                + " ORDER BY target_type, target_id, created_at, id) g ORDER BY created_at " + dir + ", id " + dir
                + " LIMIT :limit OFFSET :offset", args, GROUPED);
        return new Page(items, total);
    }

    /** Khóa dòng báo cáo để hai quản trị xử lý cùng lúc chỉ một người thành công. */
    public Optional<ContentReportView> lock(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM content_report WHERE id=:id AND deleted_at IS NULL FOR UPDATE",
                new MapSqlParameterSource("id", id), SINGLE).stream().findFirst();
    }

    /** UPDATE RETURNING nhận đúng danh sách đã đổi, không bỏ sót người báo cáo khi có request đồng thời. */
    public List<Reporter> moveReports(ContentType type, UUID targetId, ReportStatus from, ReportStatus to, UUID adminId) {
        return jdbc.query("UPDATE content_report SET status=:to, updated_by=:admin, updated_at=now() "
                + "WHERE target_type=:type AND target_id=:target AND status=:from AND deleted_at IS NULL "
                + "RETURNING id, reporter_id",
            new MapSqlParameterSource().addValue("type",type.name()).addValue("target",targetId)
                .addValue("from",from.name()).addValue("to",to.name()).addValue("admin",adminId),
            (rs,n) -> new Reporter(rs.getObject("id",UUID.class),rs.getObject("reporter_id",UUID.class)));
    }

    public Optional<ContentReportView> find(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM content_report WHERE id=:id",
                new MapSqlParameterSource("id", id), SINGLE).stream().findFirst();
    }

    /** Chủ nội dung (kể cả đã ẩn hoặc xóa) để hiển thị và ghi nhật ký. */
    public Optional<UUID> ownerOf(ContentType type, UUID id) {
        String table = table(type);
        return table == null ? Optional.empty()
                : jdbc.queryForList("SELECT created_by FROM " + table + " WHERE id=:id",
                        new MapSqlParameterSource("id", id), UUID.class).stream().findFirst();
    }

    /** hidden=true: đặt hidden_at; false: bỏ ẩn. Nội dung đã xóa thì giữ nguyên. */
    public void setHidden(ContentType type, UUID id, boolean hidden, UUID adminId) {
        String table = table(type);
        if (table != null) {
            jdbc.update("UPDATE " + table + " SET hidden_at=" + (hidden ? "now()" : "NULL")
                            + ", updated_by=:admin, updated_at=now() WHERE id=:id AND deleted_at IS NULL AND hidden_at IS "
                            + (hidden ? "NULL" : "NOT NULL"),
                    new MapSqlParameterSource().addValue("id", id).addValue("admin", adminId));
        }
    }

    private static String table(ContentType type) {
        return switch (type) {
            case POST -> "post";
            case COMMENT -> "comment";
            case EVENT -> "event";
            case ANNOUNCEMENT -> "announcement";
        };
    }
}
