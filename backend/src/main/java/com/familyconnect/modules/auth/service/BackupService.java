package com.familyconnect.modules.auth.service;

import com.familyconnect.common.audit.AuditLogger;
import com.familyconnect.common.exception.ApiException;
import com.familyconnect.common.exception.ErrorCode;
import com.familyconnect.modules.auth.dto.AdminDtos.PageResponse;
import com.familyconnect.modules.auth.dto.BackupDtos.BackupFile;
import com.familyconnect.modules.auth.dto.BackupDtos.RestoreCreateRequest;
import com.familyconnect.modules.auth.dto.BackupDtos.RestoreResult;
import com.familyconnect.modules.auth.entity.Role;
import com.familyconnect.modules.auth.repository.UserRoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** UC-AUTH-11: chỉ SYSTEM_ADMIN, không giao dịch DB xuyên suốt lệnh pg_restore. */
@Service
public class BackupService {
    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final Pattern BACKUP_ID = Pattern.compile("familyconnect-\\d{8}-\\d{6}");
    private static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String SCRIPTS = "/app/scripts/";

    private final UserRoleRepository roles;
    private final AuditLogger audit;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Path directory;
    private final ReentrantLock operation = new ReentrantLock();

    public BackupService(UserRoleRepository roles, AuditLogger audit, JdbcTemplate jdbc, Clock clock,
                         @Value("${BACKUP_STORAGE_PATH:/var/lib/familyconnect/backups}") String backupPath) {
        this.roles = roles;
        this.audit = audit;
        this.jdbc = jdbc;
        this.clock = clock;
        this.directory = Path.of(backupPath).toAbsolutePath().normalize();
    }

    public BackupFile createBackup(UUID adminId) {
        requireAdmin(adminId);
        lock();
        try {
            // Nếu đã có bản sao lưu trong cùng giây thì trả lỗi, tuyệt đối không ghi đè.
            String id = "familyconnect-" + FILE_DATE.format(OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC));
            if (Files.exists(file(id))) {
                throw new ApiException(ErrorCode.CONFLICT_001, "Đã có bản sao lưu trong giây này, vui lòng thử lại");
            }
            runScript("backup.sh", id);
            BackupFile result = describe(file(id));
            audit.log("BACKUP_CREATED", "BACKUP", null, null, Map.of("backupId", id));
            return result;
        } finally {
            operation.unlock();
        }
    }

    public PageResponse<BackupFile> listBackups(UUID adminId, int page, int size) {
        requireAdmin(adminId);
        if (page < 0 || size < 1 || size > 100) {
            throw ApiException.invalidField("page/size", "page >= 0; size từ 1 đến 100");
        }
        if (!Files.isDirectory(directory)) return PageResponse.of(List.of(), page, size, 0);
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> files = paths.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS))
                    .filter(p -> BACKUP_ID.matcher(idOf(p)).matches())
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .toList();
            long from = (long) page * size;
            if (from >= files.size()) return PageResponse.of(List.of(), page, size, files.size());
            List<BackupFile> items = files.subList((int) from, (int) Math.min(files.size(), from + size))
                    .stream().map(this::describe).toList();
            return PageResponse.of(items, page, size, files.size());
        } catch (IOException e) {
            log.error("Không thể đọc thư mục sao lưu", e);
            throw new ApiException(ErrorCode.SERVER_001);
        }
    }

    public RestoreResult restore(UUID adminId, RestoreCreateRequest request) {
        requireAdmin(adminId);
        if (!"RESTORE".equals(request.confirmText())) {
            throw ApiException.invalidField("confirmText", "Phải nhập chính xác RESTORE");
        }
        if (!BACKUP_ID.matcher(request.backupId()).matches()) {
            throw ApiException.invalidField("backupId", "Mã bản sao lưu không hợp lệ");
        }
        lock();
        try {
            Path source = file(request.backupId());
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
                throw new ApiException(ErrorCode.NOT_FOUND_001);
            }
            runScript("restore.sh", request.backupId(), "RESTORE");
            // Thu hồi refresh token sau khi DB đã phục hồi (BR-AUTH-17).
            jdbc.update("UPDATE refresh_token SET revoked_at = NOW(), updated_at = NOW() WHERE revoked_at IS NULL");
            audit.log("RESTORE_COMPLETED", "BACKUP", null, null, Map.of("backupId", request.backupId()));
            return new RestoreResult(request.backupId(), OffsetDateTime.now(clock));
        } finally {
            operation.unlock();
        }
    }

    private void runScript(String name, String... args) {
        String[] command = new String[args.length + 1];
        command[0] = SCRIPTS + name;
        System.arraycopy(args, 0, command, 1, args.length);
        Process process = null;
        try {
            process = new ProcessBuilder(command).inheritIO().start();
            if (!process.waitFor(15, TimeUnit.MINUTES)) {
                stopProcess(process);
                log.error("Tác vụ {} quá thời gian 15 phút", name);
                throw new ApiException(ErrorCode.SERVER_001);
            }
            if (process.exitValue() == 75) throw new ApiException(ErrorCode.ADM_003);
            if (process.exitValue() != 0) {
                log.error("Script {} thất bại, exit={}", name, process.exitValue());
                throw new ApiException(ErrorCode.SERVER_001);
            }
        } catch (IOException e) {
            log.error("Không thể khởi chạy script {}", name, e);
            throw new ApiException(ErrorCode.SERVER_001);
        } catch (InterruptedException e) {
            if (process != null) stopProcess(process);
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.SERVER_001);
        }
    }

    private void stopProcess(Process process) {
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly();
    }

    private void lock() {
        if (!operation.tryLock()) throw new ApiException(ErrorCode.ADM_003);
    }

    private void requireAdmin(UUID adminId) {
        if (!roles.findRoleCodes(adminId).contains(Role.SYSTEM_ADMIN)) throw new ApiException(ErrorCode.PERM_001);
    }

    private Path file(String id) { return directory.resolve(id + ".tar.gz"); }

    private String idOf(Path p) {
        String name = p.getFileName().toString();
        return name.endsWith(".tar.gz") ? name.substring(0, name.length() - 7) : "";
    }

    private BackupFile describe(Path path) {
        try {
            return new BackupFile(idOf(path), path.getFileName().toString(), Files.size(path),
                    Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().atOffset(ZoneOffset.UTC));
        } catch (IOException e) {
            log.error("Không thể đọc file sao lưu", e);
            throw new ApiException(ErrorCode.SERVER_001);
        }
    }
}
