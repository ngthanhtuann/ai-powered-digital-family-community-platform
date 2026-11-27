// Nhóm Quản trị hệ thống trên mobile, chép theo components/admin/AdminConsole.tsx của web (UC-AUTH-08 đến 12).
// Chỉ SYSTEM_ADMIN thấy menu; backend vẫn kiểm quyền (PERM_001).
import type { DrawerScreenProps } from '@react-navigation/drawer';
import type {
  NativeStackNavigationProp,
  NativeStackScreenProps,
} from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { Text, View } from 'react-native';
import { EmptyPanel } from '../components/community-ui';
import {
  Checkbox,
  ErrorText,
  Pager,
  Pill,
  Select,
  confirmAction,
  isValidDate,
} from '../components/form-ui';
import { Button, Card, Loading, Notice, Screen, TextField } from '../components/ui';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';
import {
  PAGE_SIZE,
  adminApi,
  memberRoleLabel,
  type AdminUser,
  type BackupFile,
  type ContentType,
  type GlobalRole,
  type MemberRole,
  type ReportStatus,
  type SystemConfig,
  type UserStatus,
} from '../services/admin';
import { genealogyManageApi } from '../services/genealogy';
import { useFamilyScope } from '../session/FamilyScope';
import { colors, text } from '../theme';

type Stack = NativeStackNavigationProp<RootStackParamList>;
const whenText = (v?: string | null, empty = 'Chưa có') =>
  v ? new Date(v).toLocaleString('vi-VN') : empty;
const TYPE_LABEL: Record<ContentType, string> = {
  POST: 'Bài đăng',
  COMMENT: 'Bình luận',
  EVENT: 'Sự kiện',
  ANNOUNCEMENT: 'Thông báo',
};
const REPORT_STATUS: Record<ReportStatus, string> = {
  PENDING: 'Chờ xử lý',
  HIDDEN: 'Đã ẩn',
  DISMISSED: 'Đã bỏ qua',
};

function AdminOnly({ children }: { children: ReactNode }) {
  const scope = useFamilyScope();
  if (!scope.isSystemAdmin) {
    return (
      <Screen>
        <Notice
          variant="error"
          title="PERM_001"
          message="Trang này chỉ dành cho quản trị hệ thống."
        />
      </Screen>
    );
  }
  return <>{children}</>;
}

// ---------------------------------------------------------------- Quản lý người dùng

export function AdminUsersScreen(props: DrawerScreenProps<AppDrawerParamList, 'AdminUsers'>) {
  return (
    <AdminOnly>
      <UsersSection stack={props.navigation.getParent<Stack>()} />
    </AdminOnly>
  );
}

function UsersSection({ stack }: { stack: Stack | undefined }) {
  const cache = useQueryClient();
  const scope = useFamilyScope();
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState<{ q?: string; status?: UserStatus; role?: GlobalRole }>(
    {},
  );
  const [page, setPage] = useState(0);
  const list = useQuery({
    queryKey: ['admin-users', filters, page],
    queryFn: () => adminApi.users({ ...filters, page }),
  });
  const refresh = () => cache.invalidateQueries({ queryKey: ['admin-users'] });
  const unlock = useMutation({
    mutationFn: (id: string) => adminApi.setStatus(id, { status: 'ACTIVE' }),
    onSuccess: refresh,
  });
  const roles = useMutation({
    mutationFn: ({ id, admin }: { id: string; admin: boolean }) =>
      adminApi.setRoles(id, admin ? ['MEMBER', 'SYSTEM_ADMIN'] : ['MEMBER']),
    onSuccess: refresh,
  });
  const roleText = (u: AdminUser) =>
    u.roles.includes('SYSTEM_ADMIN')
      ? 'Quản trị hệ thống'
      : u.membership.status === 'APPROVED'
        ? memberRoleLabel(u.membership.role, u.membership.branchId, u.membership.branchName)
        : 'Chưa xác minh';
  return (
    <Screen>
      <Text style={[text.body, { color: colors.textSecondary }]}>
        Tìm, lọc tài khoản; gán gia đình, vai trò; khóa và mở khóa.
      </Text>
      <TextField
        label="Tìm theo họ tên hoặc email"
        maxLength={100}
        value={search}
        onChangeText={setSearch}
      />
      <Select
        label="Trạng thái"
        value={filters.status ?? ''}
        onChange={(v) => {
          setPage(0);
          setFilters((f) => ({ ...f, status: (v || undefined) as UserStatus | undefined }));
        }}
        options={[
          { value: '', label: 'Tất cả' },
          { value: 'ACTIVE', label: 'Hoạt động' },
          { value: 'LOCKED', label: 'Đã khóa' },
        ]}
      />
      <Select
        label="Vai trò hệ thống"
        value={filters.role ?? ''}
        onChange={(v) => {
          setPage(0);
          setFilters((f) => ({ ...f, role: (v || undefined) as GlobalRole | undefined }));
        }}
        options={[
          { value: '', label: 'Tất cả' },
          { value: 'MEMBER', label: 'Thành viên' },
          { value: 'SYSTEM_ADMIN', label: 'Quản trị hệ thống' },
        ]}
      />
      <Button
        title="Tìm"
        variant="secondary"
        onPress={() => {
          setPage(0);
          setFilters((f) => ({ ...f, q: search.trim() || undefined }));
        }}
      />
      {list.isPending ? <Loading label="Đang tải người dùng..." /> : null}
      <ErrorText error={list.error ?? unlock.error ?? roles.error} />
      {list.isSuccess ? (
        <Text style={[text.caption, { color: colors.textSecondary }]}>
          {list.data.meta.total} người dùng
        </Text>
      ) : null}
      {list.isSuccess && list.data.items.length === 0 ? (
        <EmptyPanel message={filters.q ? 'Không có người dùng phù hợp.' : 'Chưa có người dùng.'} />
      ) : null}
      {list.data?.items.map((u) => {
        const isAdmin = u.roles.includes('SYSTEM_ADMIN');
        return (
          <Card key={u.id}>
            <View style={{ gap: 2 }}>
              <Text style={[text.label, { color: colors.textPrimary }]}>{u.fullName}</Text>
              <Text style={[text.caption, { color: colors.textSecondary }]}>{u.email}</Text>
              <Text style={[text.caption, { color: colors.textPrimary }]}>
                {roleText(u)} · {u.membership.familyName ?? 'Chưa thuộc gia đình'}
              </Text>
              <Text
                style={[
                  text.caption,
                  { color: u.status === 'LOCKED' ? colors.error : colors.textSecondary },
                ]}
              >
                {u.status === 'LOCKED'
                  ? `Đã khóa${u.lockReason ? `: ${u.lockReason}` : ''}`
                  : 'Hoạt động'}{' '}
                · Đăng nhập gần nhất: {whenText(u.lastLoginAt)}
              </Text>
            </View>
            {u.id === scope.userId ? (
              <Text style={[text.caption, { color: colors.textSecondary }]}>Tài khoản của bạn</Text>
            ) : (
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                {u.status === 'LOCKED' ? (
                  <Button
                    title="Mở khóa"
                    variant="secondary"
                    loading={unlock.isPending && unlock.variables === u.id}
                    onPress={() => unlock.mutate(u.id)}
                  />
                ) : (
                  <Button
                    title="Khóa"
                    variant="danger"
                    onPress={() => stack?.navigate('AdminLock', { user: u })}
                  />
                )}
                <Button
                  title="Cấp mã đặt lại mật khẩu"
                  variant="secondary"
                  onPress={() =>
                    stack?.navigate('ResetCode', { userId: u.id, fullName: u.fullName })
                  }
                />
                {!isAdmin ? (
                  <Button
                    title="Gán dòng họ"
                    variant="secondary"
                    onPress={() => stack?.navigate('AdminAssign', { user: u })}
                  />
                ) : null}
                {/* BR-AUTH-08: người đang thuộc hoặc đang xin vào dòng họ không được làm quản trị hệ thống. */}
                {isAdmin || u.membership.status === 'NONE' || u.membership.status === 'REJECTED' ? (
                  <Button
                    title={isAdmin ? 'Thu quyền quản trị' : 'Cấp quyền quản trị'}
                    variant="secondary"
                    loading={roles.isPending && roles.variables?.id === u.id}
                    onPress={() =>
                      confirmAction(
                        isAdmin ? 'Thu quyền quản trị' : 'Cấp quyền quản trị',
                        isAdmin
                          ? `Thu quyền quản trị hệ thống của ${u.fullName}?`
                          : `Cấp quyền quản trị hệ thống cho ${u.fullName}?`,
                        () => roles.mutate({ id: u.id, admin: !isAdmin }),
                      )
                    }
                  />
                ) : null}
              </View>
            )}
          </Card>
        );
      })}
      <Pager
        page={page}
        total={list.data?.meta.total ?? 0}
        pageSize={PAGE_SIZE}
        onChange={setPage}
      />
    </Screen>
  );
}

/** UC-AUTH-08 bước 3: khóa bắt buộc có lý do (BR-AUTH-13). */
export function AdminLockScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'AdminLock'>) {
  const { user } = route.params;
  const cache = useQueryClient();
  const [lockReason, setLockReason] = useState('');
  const [touched, setTouched] = useState(false);
  const missing = !lockReason.trim();
  const lock = useMutation({
    mutationFn: () =>
      adminApi.setStatus(user.id, { status: 'LOCKED', lockReason: lockReason.trim() }),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['admin-users'] });
      navigation.goBack();
    },
  });
  return (
    <Screen>
      <Card
        title={`Khóa tài khoản ${user.fullName}`}
        description="Người này bị đăng xuất ngay và không đăng nhập được cho đến khi được mở khóa."
      >
        <TextField
          label="Lý do khóa"
          maxLength={255}
          value={lockReason}
          onChangeText={setLockReason}
          onBlur={() => setTouched(true)}
          error={touched && missing ? 'Cần nhập lý do khóa' : undefined}
        />
        <ErrorText error={lock.error} />
        <Button
          title="Khóa tài khoản"
          variant="danger"
          loading={lock.isPending}
          onPress={() => (missing ? setTouched(true) : lock.mutate())}
        />
      </Card>
    </Screen>
  );
}

/** UC-AUTH-08, BR-AUTH-14: gán vào dòng họ với vai trò thành viên hoặc trưởng họ/trưởng chi, có hiệu lực ngay. */
export function AdminAssignScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'AdminAssign'>) {
  const { user } = route.params;
  const cache = useQueryClient();
  const [familyId, setFamilyId] = useState(user.membership.familyId ?? '');
  const [role, setRole] = useState<MemberRole>(user.membership.role ?? 'MEMBER');
  const [branchId, setBranchId] = useState(user.membership.branchId ?? '');
  const validFamily = /^[0-9a-f-]{36}$/i.test(familyId.trim());
  const branches = useQuery({
    queryKey: ['branches', familyId.trim()],
    queryFn: () => genealogyManageApi.branches(familyId.trim()),
    enabled: validFamily,
  });
  const save = useMutation({
    mutationFn: () =>
      adminApi.setMembership(user.id, {
        familyId: familyId.trim(),
        role,
        branchId: branchId || null,
      }),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['admin-users'] });
      navigation.goBack();
    },
  });
  return (
    <Screen>
      <Card
        title={`Gán dòng họ cho ${user.fullName}`}
        description="Nếu người này đang thuộc dòng họ khác, bản ghi cũ chuyển sang đã rời và hồ sơ gắn tài khoản được gỡ."
      >
        <TextField
          label="Mã dòng họ"
          placeholder="UUID dòng họ"
          autoCapitalize="none"
          value={familyId}
          onChangeText={setFamilyId}
          error={familyId && !validFamily ? 'Mã dòng họ là UUID' : undefined}
        />
        <Select
          label="Vai trò trong dòng họ"
          value={role}
          onChange={setRole}
          options={[
            { value: 'MEMBER', label: 'Thành viên' },
            { value: 'BRANCH_ADMIN', label: 'Trưởng họ / Trưởng chi' },
          ]}
        />
        {validFamily ? (
          <Select
            label={role === 'BRANCH_ADMIN' ? 'Chi quản lý' : 'Chi (không bắt buộc)'}
            value={branchId}
            onChange={setBranchId}
            options={[
              { value: '', label: 'Cả dòng họ (trưởng họ)' },
              ...(branches.data?.items ?? []).map((b) => ({
                value: b.id ?? '',
                label: b.name ?? '',
              })),
            ]}
          />
        ) : null}
        <ErrorText error={save.error} />
        <Button
          title="Lưu"
          onPress={() => save.mutate()}
          loading={save.isPending}
          disabled={!validFamily}
        />
      </Card>
    </Screen>
  );
}

// ---------------------------------------------------------------- Kiểm duyệt nội dung

export function AdminModerationScreen() {
  const cache = useQueryClient();
  const [status, setStatus] = useState<ReportStatus>('PENDING');
  const [targetType, setTargetType] = useState<ContentType | ''>('');
  const [page, setPage] = useState(0);
  const list = useQuery({
    queryKey: ['admin-reports', status, targetType, page],
    queryFn: () => adminApi.reports({ status, targetType: targetType || undefined, page }),
  });
  const review = useMutation({
    mutationFn: ({
      id,
      action,
    }: {
      id: string;
      action: 'HIDE_CONTENT' | 'DISMISS' | 'RESTORE_CONTENT';
    }) => adminApi.review(id, action),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['admin-reports'] }),
  });
  return (
    <AdminOnly>
      <Screen>
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Xử lý báo cáo vi phạm bài đăng, bình luận.
        </Text>
        <Select
          label="Trạng thái báo cáo"
          value={status}
          onChange={(v) => {
            setPage(0);
            setStatus(v);
          }}
          options={(Object.keys(REPORT_STATUS) as ReportStatus[]).map((s) => ({
            value: s,
            label: REPORT_STATUS[s],
          }))}
        />
        <Select
          label="Loại nội dung"
          value={targetType}
          onChange={(v) => {
            setPage(0);
            setTargetType(v);
          }}
          options={[
            { value: '' as const, label: 'Tất cả' },
            ...(Object.keys(TYPE_LABEL) as ContentType[]).map((t) => ({
              value: t,
              label: TYPE_LABEL[t],
            })),
          ]}
        />
        {list.isPending ? <Loading label="Đang tải báo cáo..." /> : null}
        <ErrorText error={list.error ?? review.error} />
        {list.isSuccess && list.data.items.length === 0 ? (
          <EmptyPanel
            message={
              status === 'PENDING'
                ? 'Không có báo cáo nào chờ xử lý.'
                : 'Chưa có báo cáo ở trạng thái này.'
            }
          />
        ) : null}
        {list.data?.items.map((r) => (
          <Card key={r.id}>
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6, alignItems: 'center' }}>
              <Pill label={TYPE_LABEL[r.targetType]} />
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                {REPORT_STATUS[r.status]}
              </Text>
              <Text
                style={[
                  text.caption,
                  { color: r.reportCount > 1 ? colors.error : colors.textSecondary },
                ]}
              >
                · {r.reportCount} lượt báo cáo
              </Text>
            </View>
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              Báo cáo đầu lúc {whenText(r.createdAt)} · Người đăng:{' '}
              {r.contentOwnerName ?? 'Tài khoản đã xóa'} · Dòng họ:{' '}
              {r.familyName ?? 'Không xác định'}
            </Text>
            <Text
              style={[
                text.body,
                {
                  color: colors.textPrimary,
                  borderLeftWidth: 4,
                  borderColor: colors.secondary,
                  paddingLeft: 12,
                },
              ]}
            >
              {r.contentSnippet || '(Không có nội dung văn bản)'}
            </Text>
            <Text style={[text.body, { color: colors.textPrimary }]}>
              <Text style={{ fontWeight: '600' }}>
                Lý do{r.reportCount > 1 ? ' (báo cáo đầu tiên)' : ''}:{' '}
              </Text>
              {r.reason}
            </Text>
            {r.status === 'HIDDEN' ? (
              <Button
                title="Khôi phục"
                variant="secondary"
                loading={review.isPending && review.variables?.id === r.id}
                onPress={() =>
                  confirmAction('Khôi phục', 'Khôi phục nội dung này lên bảng tin?', () =>
                    review.mutate({ id: r.id, action: 'RESTORE_CONTENT' }),
                  )
                }
              />
            ) : null}
            {r.status === 'PENDING' ? (
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                <Button
                  title="Ẩn nội dung"
                  variant="danger"
                  loading={
                    review.isPending &&
                    review.variables?.id === r.id &&
                    review.variables.action === 'HIDE_CONTENT'
                  }
                  onPress={() =>
                    confirmAction('Ẩn nội dung', 'Ẩn nội dung này khỏi bảng tin?', () =>
                      review.mutate({ id: r.id, action: 'HIDE_CONTENT' }),
                    )
                  }
                />
                <Button
                  title="Bỏ qua"
                  variant="secondary"
                  loading={
                    review.isPending &&
                    review.variables?.id === r.id &&
                    review.variables.action === 'DISMISS'
                  }
                  onPress={() => review.mutate({ id: r.id, action: 'DISMISS' })}
                />
              </View>
            ) : null}
          </Card>
        ))}
        <Pager
          page={page}
          total={list.data?.meta.total ?? 0}
          pageSize={PAGE_SIZE}
          onChange={setPage}
        />
      </Screen>
    </AdminOnly>
  );
}

// ---------------------------------------------------------------- Sao lưu và khôi phục

export function AdminBackupScreen() {
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<BackupFile | null>(null);
  const [confirmText, setConfirmText] = useState('');
  const [notice, setNotice] = useState('');
  const backups = useQuery({
    queryKey: ['admin-backups', page],
    queryFn: () => adminApi.backups(page),
  });
  const create = useMutation({
    mutationFn: () => adminApi.createBackup(),
    onSuccess: async (file) => {
      setNotice(`Đã tạo bản sao lưu ${file.fileName}.`);
      setPage(0);
      await cache.invalidateQueries({ queryKey: ['admin-backups'] });
    },
  });
  const restore = useMutation({
    mutationFn: (id: string) => adminApi.restore(id, 'RESTORE'),
    onSuccess: async () => {
      setNotice('Khôi phục hoàn tất. Tất cả refresh token đã bị thu hồi; vui lòng đăng nhập lại.');
      setSelected(null);
      setConfirmText('');
      await cache.invalidateQueries({ queryKey: ['admin-backups'] });
    },
  });
  const busy = create.isPending || restore.isPending;
  return (
    <AdminOnly>
      <Screen>
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Bản sao lưu gồm PostgreSQL (cả dữ liệu vector) và ảnh/tệp đã tải lên.
        </Text>
        <Button
          title="Tạo bản sao lưu"
          disabled={busy}
          loading={create.isPending}
          onPress={() => {
            setNotice('');
            create.mutate();
          }}
        />
        <ErrorText error={create.error} />
        {notice ? <Notice variant="success" title="Xong" message={notice} /> : null}
        {selected ? (
          <Card
            title={`Khôi phục ${selected.fileName}`}
            description="CẢNH BÁO: Dữ liệu cơ sở dữ liệu và toàn bộ ảnh/tệp tải lên hiện tại sẽ bị ghi đè bằng bản sao lưu đã chọn."
          >
            <TextField
              label="Nhập chính xác RESTORE để xác nhận"
              autoCapitalize="characters"
              autoCorrect={false}
              placeholder="RESTORE"
              value={confirmText}
              editable={!busy}
              onChangeText={setConfirmText}
            />
            <ErrorText error={restore.error} />
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              <Button
                title="Hủy"
                variant="secondary"
                disabled={busy}
                onPress={() => setSelected(null)}
              />
              <Button
                title="Ghi đè và khôi phục"
                variant="danger"
                loading={restore.isPending}
                disabled={confirmText !== 'RESTORE' || busy}
                onPress={() =>
                  confirmAction('Khôi phục dữ liệu', 'Xác nhận ghi đè dữ liệu hiện tại?', () => {
                    setNotice('');
                    restore.mutate(selected.id);
                  })
                }
              />
            </View>
          </Card>
        ) : null}
        {backups.isPending ? <Loading label="Đang tải danh sách..." /> : null}
        <ErrorText error={backups.error} />
        {backups.isSuccess && backups.data.items.length === 0 ? (
          <EmptyPanel message="Chưa có bản sao lưu." />
        ) : null}
        {backups.data?.items.map((file) => (
          <Card key={file.id}>
            <Text style={[text.label, { color: colors.textPrimary }]}>{file.fileName}</Text>
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              {whenText(file.createdAt)} · {(file.fileSizeBytes / (1024 * 1024)).toFixed(2)} MB
            </Text>
            <Button
              title="Khôi phục"
              variant="danger"
              disabled={busy}
              onPress={() => {
                setSelected(file);
                setConfirmText('');
                setNotice('');
              }}
            />
          </Card>
        ))}
        <Pager
          page={page}
          total={backups.data?.meta.total ?? 0}
          pageSize={PAGE_SIZE}
          onChange={setPage}
        />
      </Screen>
    </AdminOnly>
  );
}

// ---------------------------------------------------------------- Nhật ký kiểm toán

/** Tên dễ đọc cho các mã hành động hay gặp (auth.md mục 6.4); mã khác hiện nguyên. */
const ACTION_LABEL: Record<string, string> = {
  LOGIN_SUCCESS: 'Đăng nhập',
  LOGIN_FAILED: 'Đăng nhập sai',
  LOGIN_BLOCKED: 'Đăng nhập bị chặn',
  LOGOUT: 'Đăng xuất',
  USER_REGISTERED: 'Đăng ký tài khoản',
  PASSWORD_CHANGED: 'Đổi mật khẩu',
  PROFILE_UPDATED: 'Sửa hồ sơ tài khoản',
  JOIN_REQUESTED: 'Xin vào gia đình',
  JOIN_CANCELLED: 'Hủy xin vào',
  JOIN_APPROVED: 'Duyệt thành viên',
  JOIN_REJECTED: 'Từ chối thành viên',
  USER_LOCKED: 'Khóa tài khoản',
  USER_UNLOCKED: 'Mở khóa tài khoản',
  ROLE_CHANGED: 'Đổi vai trò',
  MEMBERSHIP_CHANGED: 'Đổi tư cách thành viên',
  CONTENT_DELETED: 'Gỡ nội dung',
  CONTENT_CREATED: 'Đăng nội dung',
  CONTENT_UPDATED: 'Sửa nội dung',
  CONTENT_HIDDEN: 'Ẩn nội dung',
  REPORT_DISMISSED: 'Bỏ qua báo cáo',
  CONTENT_RESTORED: 'Khôi phục nội dung',
  BACKUP_CREATED: 'Tạo sao lưu',
  RESTORE_COMPLETED: 'Khôi phục dữ liệu',
  AI_QUERY: 'Hỏi trợ lý AI',
  CONFIG_CHANGED: 'Đổi cấu hình hệ thống',
  FAMILY_CREATED: 'Tạo gia đình',
  FAMILY_UPDATED: 'Sửa gia đình',
  FAMILY_DELETED: 'Xóa gia đình',
  BRANCH_CREATED: 'Tạo chi',
  BRANCH_UPDATED: 'Sửa chi',
  BRANCH_DELETED: 'Xóa chi',
  PERSON_CREATED: 'Thêm người',
  PERSON_UPDATED: 'Sửa người',
  PERSON_DELETED: 'Xóa người',
  PARENT_CHILD_CREATED: 'Thêm quan hệ cha/mẹ - con',
  PARENT_CHILD_DELETED: 'Xóa quan hệ cha/mẹ - con',
  MARRIAGE_CREATED: 'Thêm hôn nhân',
  MARRIAGE_UPDATED: 'Sửa hôn nhân',
  MARRIAGE_DELETED: 'Xóa hôn nhân',
  MEMBERS_CSV_EXPORTED: 'Xuất CSV thành viên',
  OUTSTANDING_MEMBER_CREATED: 'Thêm người tiêu biểu',
  OUTSTANDING_MEMBER_UPDATED: 'Sửa người tiêu biểu',
  OUTSTANDING_MEMBER_DELETED: 'Xóa người tiêu biểu',
};
const ROLE_LABEL: Record<string, string> = {
  MEMBER: 'Thành viên',
  BRANCH_ADMIN: 'Trưởng họ/chi',
  SYSTEM_ADMIN: 'Quản trị',
};
const shortId = (id?: string | null) => (id ? id.slice(0, 8) : '');
/** Ngày YYYY-MM-DD (giờ máy) thành ISO-8601 kèm múi giờ, đầu hoặc cuối ngày. */
const dayBound = (day: string, end: boolean) =>
  day && isValidDate(day)
    ? new Date(`${day}T${end ? '23:59:59.999' : '00:00:00'}`).toISOString()
    : undefined;

export function AdminAuditScreen() {
  const scope = useFamilyScope();
  const [action, setAction] = useState('');
  const [fromDay, setFromDay] = useState('');
  const [toDay, setToDay] = useState('');
  const [actor, setActor] = useState<string | null>(null);
  const [page, setPage] = useState(0);
  // Theo dõi hoạt động một gia đình: gia đình đang chọn ở ô "Gia đình đang xem" (chỉ hành động, không nội dung riêng tư).
  const viewingFamily = scope.familyId;
  const [onlyFamily, setOnlyFamily] = useState(true);
  const badDay = (fromDay && !isValidDate(fromDay)) || (toDay && !isValidDate(toDay));
  const badRange = Boolean(fromDay && toDay && fromDay > toDay);
  const params = {
    action: action || undefined,
    from: dayBound(fromDay, false),
    to: dayBound(toDay, true),
    actorUserId: actor ?? undefined,
    familyId: (onlyFamily && viewingFamily) || undefined,
    page,
  };
  const list = useQuery({
    queryKey: ['admin-audit-logs', params],
    queryFn: () => adminApi.auditLogs(params),
    enabled: !badRange && !badDay,
  });
  const filter = (apply: () => void) => {
    setPage(0);
    apply();
  };
  return (
    <AdminOnly>
      <Screen>
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Các thao tác nhạy cảm: ai làm, làm gì, lúc nào, từ IP nào. Chỉ xem, không sửa hay xóa
          được.
        </Text>
        <Select
          label="Hành động"
          value={action}
          onChange={(v) => filter(() => setAction(v))}
          options={[
            { value: '', label: 'Tất cả' },
            ...Object.entries(ACTION_LABEL).map(([value, label]) => ({ value, label })),
          ]}
        />
        <TextField
          label="Từ ngày (YYYY-MM-DD)"
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={fromDay}
          onChangeText={(v) => filter(() => setFromDay(v))}
        />
        <TextField
          label="Đến ngày (YYYY-MM-DD)"
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={toDay}
          onChangeText={(v) => filter(() => setToDay(v))}
        />
        {viewingFamily ? (
          <Checkbox
            checked={onlyFamily}
            onChange={(v) => filter(() => setOnlyFamily(v))}
            label="Chỉ hoạt động của gia đình đang xem"
          />
        ) : null}
        {actor ? (
          <View style={{ gap: 8 }}>
            <Text style={[text.body, { color: colors.textPrimary }]}>
              Đang lọc theo người thực hiện{' '}
              <Text style={{ fontWeight: '600' }}>{shortId(actor)}</Text>
            </Text>
            <Button
              title="Bỏ lọc người"
              variant="secondary"
              onPress={() => filter(() => setActor(null))}
            />
          </View>
        ) : null}
        {badDay ? (
          <Notice variant="error" title="VALID_001" message="Ngày theo dạng YYYY-MM-DD." />
        ) : null}
        {badRange ? (
          <Notice
            variant="error"
            title="VALID_001"
            message="“Từ ngày” phải trước hoặc bằng “Đến ngày”."
          />
        ) : null}
        {!badRange && !badDay && list.isPending ? <Loading label="Đang tải nhật ký..." /> : null}
        <ErrorText error={list.error} />
        {list.isSuccess ? (
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            {list.data.meta.total} bản ghi
          </Text>
        ) : null}
        {list.isSuccess && list.data.items.length === 0 ? (
          <EmptyPanel message="Không có bản ghi phù hợp." />
        ) : null}
        {list.data?.items.map((r) => (
          <Card key={r.id}>
            <Text style={[text.label, { color: colors.textPrimary }]}>
              {ACTION_LABEL[r.action] ?? r.action}
            </Text>
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              {r.action} · {whenText(r.createdAt)}
            </Text>
            <Text style={[text.caption, { color: colors.textPrimary }]}>
              {r.actorUserId
                ? `${ROLE_LABEL[r.actorRole ?? ''] ?? 'Không rõ vai trò'} · ${shortId(r.actorUserId)}`
                : 'Người thực hiện: chưa xác định'}
              {r.targetType
                ? ` · ${r.targetType}${r.targetId ? ` ${shortId(r.targetId)}` : ''}`
                : ''}
              {r.ipAddress ? ` · IP ${r.ipAddress}` : ''}
            </Text>
            {Object.keys(r.detail ?? {}).length ? (
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                {Object.entries(r.detail ?? {})
                  .map(([k, v]) => `${k}: ${typeof v === 'object' ? JSON.stringify(v) : String(v)}`)
                  .join('; ')}
              </Text>
            ) : null}
            {r.actorUserId && actor !== r.actorUserId ? (
              <Button
                title="Chỉ xem người này"
                variant="secondary"
                onPress={() => filter(() => setActor(r.actorUserId ?? null))}
              />
            ) : null}
          </Card>
        ))}
        <Pager
          page={page}
          total={list.data?.meta.total ?? 0}
          pageSize={PAGE_SIZE}
          onChange={setPage}
        />
      </Screen>
    </AdminOnly>
  );
}

// ---------------------------------------------------------------- Cấu hình hệ thống

/** Ô chọn cho khóa có tập giá trị cố định (auth.md mục 5.3); khóa khác dùng ô nhập. */
const CONFIG_CHOICES: Record<string, { value: string; label: string }[]> = {
  'membership.approval_mode': [
    { value: 'MANUAL', label: 'Thủ công (trưởng họ, trưởng chi duyệt)' },
    { value: 'AUTO', label: 'Tự động duyệt ngay' },
  ],
};
const BOOLEAN_CHOICES = [
  { value: 'true', label: 'Bật' },
  { value: 'false', label: 'Tắt' },
];

function ConfigRow({ config }: { config: SystemConfig }) {
  const cache = useQueryClient();
  const [draft, setDraft] = useState(config.value);
  const [saved, setSaved] = useState('');
  const save = useMutation({
    mutationFn: (value: string) => adminApi.updateConfig(config.key, value),
    onSuccess: async (updated) => {
      setDraft(updated.value);
      setSaved('Đã lưu, có hiệu lực trong vòng 1 phút.');
      await cache.invalidateQueries({ queryKey: ['admin-configs'] });
    },
  });
  const choices = config.valueType === 'BOOLEAN' ? BOOLEAN_CHOICES : CONFIG_CHOICES[config.key];
  const empty = !draft.trim();
  return (
    <Card>
      <Text style={[text.label, { color: colors.textPrimary }]}>{config.description}</Text>
      <Text style={[text.caption, { color: colors.textSecondary }]}>
        {config.key} · Mặc định: {config.defaultValue} · Cập nhật: {whenText(config.updatedAt)}
      </Text>
      {choices ? (
        <Select
          label="Giá trị"
          value={draft}
          options={choices}
          onChange={setDraft}
          disabled={save.isPending}
        />
      ) : (
        <TextField
          label="Giá trị"
          keyboardType={config.valueType === 'INTEGER' ? 'number-pad' : 'default'}
          maxLength={500}
          editable={!save.isPending}
          value={draft}
          onChangeText={setDraft}
          error={empty ? 'Không được để trống' : undefined}
        />
      )}
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button
          title="Lưu"
          loading={save.isPending}
          disabled={empty || draft.trim() === config.value}
          onPress={() => {
            setSaved('');
            save.mutate(draft.trim());
          }}
        />
        <Button
          title="Khôi phục mặc định"
          variant="secondary"
          disabled={save.isPending || config.value === config.defaultValue}
          onPress={() => {
            setSaved('');
            save.mutate(config.defaultValue);
          }}
        />
      </View>
      <ErrorText error={save.error} />
      {saved ? <Text style={[text.body, { color: colors.success }]}>{saved}</Text> : null}
    </Card>
  );
}

/** UC-AUTH-12 (SCRUM-94): tham số hệ thống nhóm theo mục. Khóa bí mật chỉ ở biến môi trường, không hiện ở đây. */
export function AdminConfigScreen() {
  const configs = useQuery({ queryKey: ['admin-configs'], queryFn: () => adminApi.configs() });
  const groups = new Map<string, SystemConfig[]>();
  configs.data?.items.forEach((c) => groups.set(c.group, [...(groups.get(c.group) ?? []), c]));
  return (
    <AdminOnly>
      <Screen>
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Thay đổi áp dụng trong vòng 1 phút, không cần khởi động lại. Mọi thay đổi được ghi vào
          nhật ký kiểm toán.
        </Text>
        {configs.isPending ? <Loading label="Đang tải cấu hình..." /> : null}
        <ErrorText error={configs.error} />
        {configs.isSuccess && configs.data.items.length === 0 ? (
          <EmptyPanel message="Chưa có tham số cấu hình." />
        ) : null}
        {[...groups.entries()].map(([group, items]) => (
          <View key={group} style={{ gap: 12 }}>
            <Text style={[text.label, { color: colors.textSecondary }]}>{group}</Text>
            {items.map((c) => (
              <ConfigRow key={`${c.key}-${c.updatedAt}`} config={c} />
            ))}
          </View>
        ))}
      </Screen>
    </AdminOnly>
  );
}
