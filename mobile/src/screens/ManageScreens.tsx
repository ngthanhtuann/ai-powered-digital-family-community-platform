// Nhóm Quản lý trên mobile, cùng nghiệp vụ với web: /members, /join-requests (UC-AUTH-07), /family-roles (UC-AUTH-14,
// BR-GEN-29, 30). Hộp thoại của web là màn của stack (Duyệt, Từ chối, Thêm/Sửa chi, Chọn trưởng chi, Phân công, Mời).
import type { DrawerScreenProps } from '@react-navigation/drawer';
import type {
  NativeStackNavigationProp,
  NativeStackScreenProps,
} from '@react-navigation/native-stack';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Pressable, Share, Text, View } from 'react-native';
import { AdminFamilyPicker } from '../components/AdminFamilyPicker';
import { EmptyPanel } from '../components/community-ui';
import { ErrorText, Pager, Pill, Select } from '../components/form-ui';
import { PersonAvatar } from '../components/genealogy-ui';
import { Button, Card, Loading, Notice, Screen, TextField } from '../components/ui';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';
import {
  PAGE_SIZE,
  familyRolesApi,
  joinRequestsApi,
  memberRoleLabel,
  type JoinRequestStatus,
  type MemberRole,
} from '../services/admin';
import {
  genealogyApi,
  genealogyManageApi,
  yearOf,
  type Branch,
  type Person,
} from '../services/genealogy';
import { useFamilyScope } from '../session/FamilyScope';
import { colors, radius, text } from '../theme';

type Stack = NativeStackNavigationProp<RootStackParamList>;
type StackProps<K extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList, K>;

const dayText = (v?: string | null) => (v ? v.slice(0, 10).split('-').reverse().join('/') : '');
const whenText = (v?: string | null) => (v ? new Date(v).toLocaleString('vi-VN') : '');
/** Họ tên bỏ dấu, chữ thường, gộp khoảng trắng: so khớp trùng tên giống tìm kiếm không dấu của backend. */
const plainName = (s?: string | null) =>
  (s ?? '')
    .normalize('NFD')
    .replace(/[̀-ͯ]/g, '')
    .replace(/đ/gi, 'd')
    .toLowerCase()
    .replace(/\s+/g, ' ')
    .trim();
const personLabel = (p: Person) =>
  `${p.fullName}${p.birthDate ? ` (${dayText(p.birthDate)})` : p.birthYear ? ` (${p.birthYear})` : ''}`;
const matches = (q: string, ...texts: (string | null | undefined)[]) =>
  !q || texts.some((s) => s?.toLocaleLowerCase('vi').includes(q.toLocaleLowerCase('vi')));

function useBranches(familyId: string | null) {
  return useQuery({
    queryKey: ['branches', familyId],
    queryFn: () => genealogyManageApi.branches(familyId as string),
    enabled: Boolean(familyId),
  });
}

function Denied({ message }: { message: string }) {
  return (
    <Screen>
      <Notice variant="error" title="PERM_001" message={message} />
    </Screen>
  );
}

// ---------------------------------------------------------------- Thành viên (web /members)

/** UC-GEN-03: tìm thành viên, thêm hồ sơ và quan hệ gia đình. */
export function MembersScreen({
  navigation: drawer,
}: DrawerScreenProps<AppDrawerParamList, 'Members'>) {
  const stack = drawer.getParent<Stack>();
  const scope = useFamilyScope();
  const familyId = scope.familyId;
  const [search, setSearch] = useState('');
  const [q, setQ] = useState('');
  const [page, setPage] = useState(0);
  const list = useQuery({
    queryKey: ['members', familyId, q, page],
    enabled: Boolean(familyId),
    queryFn: () =>
      genealogyManageApi.persons(familyId as string, {
        q: q || undefined,
        page,
        size: PAGE_SIZE,
        sort: 'fullName,asc',
      }),
  });
  const me = useQuery({ queryKey: ['person', 'me'], queryFn: genealogyApi.myPerson, retry: false });
  const runSearch = () => {
    setPage(0);
    setQ(search.trim());
  };
  return (
    <Screen>
      <Text style={[text.body, { color: colors.textSecondary }]}>
        Tìm thành viên, thêm hồ sơ và quan hệ gia đình.
      </Text>
      {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
      {scope.isAdmin && familyId ? (
        <Button title="Thêm thành viên" onPress={() => stack?.navigate('PersonForm', {})} />
      ) : null}
      {!familyId ? <EmptyPanel message="Chưa chọn gia đình." /> : null}
      {familyId ? (
        <>
          <TextField
            label="Tìm theo họ tên"
            value={search}
            onChangeText={setSearch}
            returnKeyType="search"
            onSubmitEditing={runSearch}
          />
          <Button title="Tìm" variant="secondary" onPress={runSearch} />
          {list.isPending ? <Loading label="Đang tải danh sách..." /> : null}
          <ErrorText error={list.error} />
          {list.isSuccess && list.data.items.length === 0 ? (
            <EmptyPanel
              message={q ? 'Không có thành viên phù hợp.' : 'Gia đình chưa có thành viên.'}
            />
          ) : null}
          {list.data?.items.map((p) => (
            <Card key={p.id}>
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`Mở trang cá nhân ${p.fullName}`}
                onPress={() => stack?.navigate('PersonDetail', { personId: p.id! })}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 44 }}
              >
                <PersonAvatar name={p.fullName} gender={p.gender} mediaId={p.avatarMediaId} />
                <View style={{ flex: 1 }}>
                  <Text style={[text.label, { color: colors.textPrimary }]}>{p.fullName}</Text>
                  <Text style={[text.caption, { color: colors.textSecondary }]}>
                    {[yearOf(p) && `Sinh ${yearOf(p)}`, p.currentProvince, p.isDeceased && 'Đã mất']
                      .filter(Boolean)
                      .join(' · ') || 'Chưa có thông tin'}
                  </Text>
                </View>
              </Pressable>
              {p.canEdit || scope.isAdmin ? (
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  {/* BR-GEN-20: nút Sửa theo canEdit do backend tính (chính chủ, hoặc quản trị với hồ sơ chưa có tài khoản). */}
                  {p.canEdit ? (
                    <Button
                      title="Sửa"
                      variant="secondary"
                      onPress={() =>
                        p.id === me.data?.id
                          ? stack?.navigate('EditProfile', { personId: p.id! })
                          : stack?.navigate('PersonForm', { personId: p.id! })
                      }
                    />
                  ) : null}
                  {scope.isAdmin ? (
                    <Button
                      title="Thêm cha/mẹ"
                      variant="secondary"
                      onPress={() =>
                        stack?.navigate('RelativeForm', { personId: p.id!, mode: 'parent' })
                      }
                    />
                  ) : null}
                  {scope.isAdmin ? (
                    <Button
                      title="Thêm vợ/chồng"
                      variant="secondary"
                      onPress={() =>
                        stack?.navigate('RelativeForm', { personId: p.id!, mode: 'spouse' })
                      }
                    />
                  ) : null}
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
        </>
      ) : null}
    </Screen>
  );
}

// ---------------------------------------------------------------- Duyệt yêu cầu tham gia (web /join-requests)

const STATUS_LABEL: Partial<Record<JoinRequestStatus, string>> = {
  PENDING: 'Chờ duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Đã từ chối',
  CANCELLED: 'Người xin đã hủy',
};

/** BR-AUTH-09: mã để mời. Mã chi đưa yêu cầu tới trưởng chi đó; mã gia đình tới trưởng họ. */
function InviteCodes({ familyId, myBranchId }: { familyId: string; myBranchId: string | null }) {
  const branches = useBranches(familyId);
  const all = branches.data?.items ?? [];
  // Trưởng chi: chi mình và các chi con (đi theo parentBranchId).
  const visible =
    myBranchId === null
      ? all
      : all.filter((b) => {
          for (
            let id: string | null | undefined = b.id;
            id;
            id = all.find((x) => x.id === id)?.parentBranchId
          ) {
            if (id === myBranchId) return true;
          }
          return false;
        });
  const codes = [
    ...(myBranchId === null ? [{ label: 'Mã gia đình (cả dòng họ)', code: familyId }] : []),
    ...visible.map((b) => ({ label: `Mã chi ${b.name}`, code: b.id ?? '' })),
  ];
  return (
    <Card
      title="Mã để mời người thân"
      description="Gửi mã chi để yêu cầu tới thẳng trưởng chi đó; mã gia đình thì trưởng họ duyệt."
    >
      {codes.map((c) => (
        <View
          key={c.code}
          style={{ gap: 4, borderTopWidth: 1, borderColor: colors.border, paddingTop: 8 }}
        >
          <Text style={[text.label, { color: colors.textPrimary }]}>{c.label}</Text>
          <Text selectable style={[text.caption, { color: colors.textSecondary }]}>
            {c.code}
          </Text>
          <Button
            title="Gửi mã"
            variant="secondary"
            onPress={() => Share.share({ message: c.code })}
          />
        </View>
      ))}
      {branches.isError ? (
        <Text style={[text.caption, { color: colors.error }]}>Không tải được danh sách chi.</Text>
      ) : null}
    </Card>
  );
}

/** UC-AUTH-07: trưởng họ, trưởng chi (trong phạm vi chi) và quản trị duyệt yêu cầu tham gia. */
export function JoinRequestsScreen({
  navigation: drawer,
}: DrawerScreenProps<AppDrawerParamList, 'JoinRequests'>) {
  const stack = drawer.getParent<Stack>();
  const scope = useFamilyScope();
  const [status, setStatus] = useState<JoinRequestStatus>('PENDING');
  const [page, setPage] = useState(0);
  const familyFilter = scope.isSystemAdmin ? (scope.familyId ?? undefined) : undefined;
  const list = useQuery({
    queryKey: ['join-requests', status, familyFilter, page],
    enabled: scope.isAdmin,
    queryFn: () => joinRequestsApi.list({ status, familyId: familyFilter, page }),
  });
  if (!scope.isAdmin)
    return (
      <Denied message="Chỉ trưởng họ, trưởng chi và quản trị hệ thống mới duyệt được yêu cầu." />
    );
  return (
    <Screen>
      <Text style={[text.body, { color: colors.textSecondary }]}>
        Người thân xin vào bằng mã gia đình hoặc mã chi. Trưởng chi thấy yêu cầu gửi bằng mã chi
        mình và chi con.
      </Text>
      {scope.familyId && !scope.isSystemAdmin ? (
        <InviteCodes familyId={scope.familyId} myBranchId={scope.branchId} />
      ) : null}
      {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
      <Select
        label="Trạng thái"
        value={status}
        onChange={(v) => {
          setPage(0);
          setStatus(v);
        }}
        options={(Object.entries(STATUS_LABEL) as [JoinRequestStatus, string][]).map(
          ([value, label]) => ({
            value,
            label,
          }),
        )}
      />
      {list.isPending ? <Loading label="Đang tải yêu cầu..." /> : null}
      <ErrorText error={list.error} />
      {list.isSuccess && list.data.items.length === 0 ? (
        <EmptyPanel
          message={
            status === 'PENDING'
              ? 'Không có yêu cầu nào đang chờ duyệt.'
              : 'Chưa có yêu cầu ở trạng thái này.'
          }
        />
      ) : null}
      {list.data?.items.map((r) => (
        <Card key={r.id}>
          <Text style={[text.label, { color: colors.textPrimary }]}>{r.applicantName}</Text>
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            {STATUS_LABEL[r.status] ?? r.status} · {whenText(r.createdAt)}
          </Text>
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            {r.applicantEmail}
            {r.applicantDateOfBirth ? ` · Ngày sinh ${dayText(r.applicantDateOfBirth)}` : ''}
          </Text>
          {r.message ? (
            <Text style={[text.body, { color: colors.textPrimary }]}>“{r.message}”</Text>
          ) : null}
          {r.status === 'REJECTED' && r.rejectReason ? (
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              Lý do từ chối: {r.rejectReason}
            </Text>
          ) : null}
          {r.status === 'PENDING' ? (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              <Button
                title="Duyệt"
                onPress={() =>
                  stack?.navigate('ApproveRequest', { request: r, canPickBranch: !scope.branchId })
                }
              />
              <Button
                title="Từ chối"
                variant="secondary"
                onPress={() => stack?.navigate('RejectRequest', { request: r })}
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
  );
}

/**
 * UC-AUTH-07 bước 2: duyệt, có thể gắn với hồ sơ có sẵn (BR-AUTH-11). Tự chọn sẵn hồ sơ chưa gắn tài khoản trùng cả
 * họ tên và ngày sinh nếu chỉ có một (SCRUM-132); người duyệt vẫn xem lại và đổi được.
 */
export function ApproveRequestScreen({ navigation, route }: StackProps<'ApproveRequest'>) {
  const { request, canPickBranch } = route.params;
  const cache = useQueryClient();
  const [q, setQ] = useState(request.applicantName ?? '');
  const [picked, setPicked] = useState<Person | null>(null);
  const [touched, setTouched] = useState(false);
  const [branchId, setBranchId] = useState(request.branchId ?? '');
  const branches = useBranches(canPickBranch ? request.familyId : null);
  const persons = useQuery({
    queryKey: ['join-persons', request.familyId, q.trim()],
    queryFn: () =>
      genealogyManageApi.persons(request.familyId, {
        q: q.trim() || undefined,
        page: 0,
        size: 100,
        sort: 'fullName,asc',
      }),
    placeholderData: keepPreviousData,
  });
  // Hồ sơ đã gắn tài khoản khác thì không gắn được nữa (user_id duy nhất), nên không cho chọn.
  const free = persons.data?.items.filter((p) => !p.userId) ?? [];
  const sameName = free.filter((p) => plainName(p.fullName) === plainName(request.applicantName));
  const sameBirth = sameName.filter((p) => p.birthDate === request.applicantDateOfBirth);
  // Chưa đụng vào ô nào thì dùng hồ sơ gợi ý; đụng vào rồi thì giữ lựa chọn của người duyệt.
  const suggested = !touched && sameBirth.length === 1 ? sameBirth[0] : null;
  const chosen = touched ? picked : suggested;
  const chosenBranch = canPickBranch && suggested?.branchId ? suggested.branchId : branchId;
  const autoPicked = Boolean(chosen) && sameBirth.length === 1 && chosen?.id === sameBirth[0].id;
  const options = chosen && !free.some((p) => p.id === chosen.id) ? [chosen, ...free] : free;
  const more = (persons.data?.meta.total ?? 0) > (persons.data?.items.length ?? 0);
  const freeze = () => {
    if (touched) return;
    setTouched(true);
    setPicked(chosen);
    setBranchId(chosenBranch);
  };
  const choose = (person: Person | null) => {
    setTouched(true);
    setPicked(person);
    if (canPickBranch && person?.branchId) setBranchId(person.branchId);
  };
  const approve = useMutation({
    mutationFn: () =>
      joinRequestsApi.review(request.id, {
        status: 'APPROVED',
        personId: chosen?.id ?? null,
        branchId:
          canPickBranch && chosenBranch !== (request.branchId ?? '') ? chosenBranch || null : null,
      }),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['join-requests'] });
      navigation.goBack();
    },
  });
  return (
    <Screen>
      <Card
        title={`Duyệt ${request.applicantName}`}
        description="Người này sẽ thành thành viên đã xác minh và xem được dữ liệu gia đình ngay."
      >
        {request.applicantDateOfBirth ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>
            Ngày sinh người xin: {dayText(request.applicantDateOfBirth)}
          </Text>
        ) : null}
        <TextField
          label="Tìm hồ sơ theo tên"
          value={q}
          onChangeText={(v) => {
            freeze();
            setQ(v);
          }}
        />
        <Select
          label="Gắn với hồ sơ trong cây gia phả (không bắt buộc)"
          value={chosen?.id ?? ''}
          onChange={(id) => choose(options.find((p) => p.id === id) ?? null)}
          options={[
            { value: '', label: 'Không gắn' },
            ...options.map((p) => ({ value: p.id ?? '', label: personLabel(p) })),
          ]}
        />
        {autoPicked ? (
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            Đã chọn sẵn hồ sơ trùng họ tên và ngày sinh. Kiểm tra lại trước khi duyệt.
          </Text>
        ) : null}
        {!chosen && persons.isSuccess && sameName.length > 0 && sameBirth.length !== 1 ? (
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            Có {sameName.length} hồ sơ trùng họ tên nhưng{' '}
            {sameBirth.length > 1 ? 'trùng cả ngày sinh' : 'khác ngày sinh'}. Đọc lời nhắn (tên cha
            mẹ) để chọn đúng người.
          </Text>
        ) : null}
        {!chosen &&
        persons.isSuccess &&
        sameName.length === 0 &&
        plainName(q) === plainName(request.applicantName) ? (
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            Chưa có hồ sơ trùng họ tên trên cây. Có thể để Không gắn, duyệt rồi thêm người này lên
            cây sau.
          </Text>
        ) : null}
        {more ? (
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            Còn nhiều người hơn, gõ thêm tên để thu hẹp.
          </Text>
        ) : null}
        {persons.isError ? (
          <Text style={[text.caption, { color: colors.error }]}>
            Không tải được danh sách thành viên.
          </Text>
        ) : null}
        {canPickBranch ? (
          <Select
            label="Chi của thành viên"
            value={chosenBranch}
            onChange={(v) => {
              freeze();
              setBranchId(v);
            }}
            options={[
              { value: '', label: 'Không thuộc chi nào (cả dòng họ)' },
              ...(branches.data?.items ?? []).map((b) => ({
                value: b.id ?? '',
                label: b.name ?? '',
              })),
            ]}
          />
        ) : null}
        <ErrorText error={approve.error} />
        <Button title="Duyệt" onPress={() => approve.mutate()} loading={approve.isPending} />
      </Card>
    </Screen>
  );
}

export function RejectRequestScreen({ navigation, route }: StackProps<'RejectRequest'>) {
  const { request } = route.params;
  const cache = useQueryClient();
  const [reason, setReason] = useState('');
  const reject = useMutation({
    mutationFn: () =>
      joinRequestsApi.review(request.id, {
        status: 'REJECTED',
        rejectReason: reason.trim() || null,
      }),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['join-requests'] });
      navigation.goBack();
    },
  });
  return (
    <Screen>
      <Card
        title={`Từ chối ${request.applicantName}`}
        description="Người xin sẽ thấy lý do và có thể gửi yêu cầu mới."
      >
        <TextField
          label="Lý do (không bắt buộc)"
          maxLength={255}
          value={reason}
          onChangeText={setReason}
        />
        <ErrorText error={reject.error} />
        <Button
          title="Từ chối"
          variant="danger"
          onPress={() => reject.mutate()}
          loading={reject.isPending}
        />
      </Card>
    </Screen>
  );
}

// ---------------------------------------------------------------- Phân công trưởng chi (web /family-roles)

/** Chi xếp thành cây: chi gốc trước, chi con thụt vào; chi mồ côi (cha đã xóa) xếp cuối ở độ sâu 0. */
function branchRows(branches: Branch[]) {
  const rows: { branch: Branch; depth: number }[] = [];
  const seen = new Set<string>();
  const visit = (parent: string | null, depth: number) => {
    branches
      .filter((b) => (b.parentBranchId ?? null) === parent && !seen.has(b.id ?? ''))
      .sort((a, b) => (a.name ?? '').localeCompare(b.name ?? '', 'vi'))
      .forEach((b) => {
        seen.add(b.id ?? '');
        rows.push({ branch: b, depth });
        visit(b.id ?? null, depth + 1);
      });
  };
  visit(null, 0);
  branches.filter((b) => !seen.has(b.id ?? '')).forEach((b) => rows.push({ branch: b, depth: 0 }));
  return rows;
}

/** Người có thể mời: còn sống, chưa gắn tài khoản, đủ 16 tuổi (BR-GEN-16). */
function invitable(p: Person, today: Date) {
  if (p.isDeceased || p.userId || !p.birthDate) return false;
  const born = new Date(p.birthDate);
  born.setFullYear(born.getFullYear() + 16);
  return born <= today;
}

function usePeople(familyId: string) {
  // shortcut: tối đa 100 người (giới hạn size của API) như web; dòng họ lớn hơn cần phân trang.
  return useQuery({
    queryKey: ['members', familyId, 'invite'],
    queryFn: () =>
      genealogyManageApi.persons(familyId, { page: 0, size: 100, sort: 'fullName,asc' }),
  });
}

/** UC-AUTH-14: trưởng họ (BRANCH_ADMIN không gán chi) hoặc quản trị bổ nhiệm, thu quyền trưởng chi. */
export function FamilyRolesScreen({
  navigation: drawer,
}: DrawerScreenProps<AppDrawerParamList, 'FamilyRoles'>) {
  const stack = drawer.getParent<Stack>();
  const scope = useFamilyScope();
  const familyId = scope.isFamilyHead ? (scope.familyId ?? '') : '';
  const [page, setPage] = useState(0);
  const list = useQuery({
    queryKey: ['family-accounts', familyId, page],
    enabled: Boolean(familyId),
    queryFn: () => familyRolesApi.accounts(familyId, page),
  });
  if (!scope.isFamilyHead) {
    return (
      <Denied message="Chỉ trưởng họ (người quản lý cả dòng họ) mới phân công được trưởng chi." />
    );
  }
  return (
    <Screen>
      <Text style={[text.body, { color: colors.textSecondary }]}>
        Trưởng họ xem từng chi đã có trưởng chi chưa, chọn người quản lý, mời người thân chưa có tài
        khoản, hoặc thu quyền về thành viên.
      </Text>
      {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
      {!familyId ? <EmptyPanel message="Chưa chọn gia đình." /> : null}
      {list.isPending && familyId ? <Loading label="Đang tải danh sách..." /> : null}
      <ErrorText error={list.error} />
      {familyId && list.isSuccess ? <BranchOverview familyId={familyId} stack={stack} /> : null}
      {list.isSuccess ? (
        <Card title="Tất cả tài khoản">
          {list.data.items.length === 0 ? (
            <Text style={[text.body, { color: colors.textSecondary }]}>
              Dòng họ chưa có tài khoản nào được duyệt.
            </Text>
          ) : null}
          {list.data.items.map((a) => (
            <View
              key={a.userId}
              style={{ gap: 8, borderTopWidth: 1, borderColor: colors.border, paddingTop: 8 }}
            >
              <Text style={[text.label, { color: colors.textPrimary }]}>{a.fullName}</Text>
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                {memberRoleLabel(a.role, a.branchId, a.branchName)}
              </Text>
              {a.userId === scope.userId ? (
                <Text style={[text.caption, { color: colors.textSecondary }]}>
                  Tài khoản của bạn
                </Text>
              ) : (
                <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                  <Button
                    title="Phân công"
                    variant="secondary"
                    onPress={() => stack?.navigate('AccountRole', { familyId, account: a })}
                  />
                  {/* BR-AUTH-24: trưởng họ không cấp mã cho trưởng họ khác; quản trị thì được. */}
                  {scope.isSystemAdmin || a.role !== 'BRANCH_ADMIN' || a.branchId ? (
                    <Button
                      title="Cấp mã đặt lại mật khẩu"
                      variant="secondary"
                      onPress={() =>
                        stack?.navigate('ResetCode', { userId: a.userId, fullName: a.fullName })
                      }
                    />
                  ) : null}
                </View>
              )}
            </View>
          ))}
          <Pager page={page} total={list.data.meta.total} pageSize={PAGE_SIZE} onChange={setPage} />
        </Card>
      ) : null}
    </Screen>
  );
}

/** SCRUM-137, 146, 147: phân công theo chi; chi chưa có trưởng chi hiện "Chưa có"; mời người chưa có tài khoản. */
function BranchOverview({ familyId, stack }: { familyId: string; stack: Stack | undefined }) {
  const [q, setQ] = useState('');
  const accounts = useQuery({
    queryKey: ['family-accounts', familyId, 'all'],
    queryFn: () => familyRolesApi.accounts(familyId, 0, 100),
  });
  const branches = useBranches(familyId);
  const branchHeads = useQuery({
    queryKey: ['branch-heads', familyId],
    queryFn: () => genealogyManageApi.branchHeads(familyId),
  });
  const persons = usePeople(familyId);
  const headOf = new Map((branchHeads.data ?? []).map((h) => [h.branchId ?? '', h]));
  const heads = (accounts.data?.items ?? []).filter(
    (a) => a.role === 'BRANCH_ADMIN' && !a.branchId,
  );
  const branchList = branches.data?.items ?? [];
  const branchName = (id?: string | null) => branchList.find((b) => b.id === id)?.name;
  const today = new Date();
  const toInvite = (persons.data?.items ?? []).filter(
    (p) => invitable(p, today) && matches(q, p.fullName, branchName(p.branchId)),
  );
  const rows = branchRows(branchList).filter(({ branch }) =>
    matches(q, branch.name, headOf.get(branch.id ?? '')?.headPersonName),
  );
  return (
    <>
      <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>Theo chi</Text>
      <TextField
        label="Tìm theo tên người hoặc tên chi"
        value={q}
        onChangeText={setQ}
        placeholder="Ví dụ: Nguyễn Văn An"
      />
      {accounts.isPending || branches.isPending ? <Loading label="Đang tải..." /> : null}
      <ErrorText error={accounts.error ?? branches.error ?? persons.error ?? branchHeads.error} />
      <Card title="Trưởng họ">
        <Text style={[text.body, { color: colors.textPrimary }]}>
          {heads.length ? heads.map((h) => h.fullName).join(', ') : 'Chưa có'}
        </Text>
      </Card>
      <Button title="Thêm chi" onPress={() => stack?.navigate('BranchForm', { familyId })} />
      <Card title="Các chi">
        {branches.isSuccess && rows.length === 0 ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>
            {q ? 'Không có chi phù hợp.' : 'Dòng họ chưa có chi nào.'}
          </Text>
        ) : null}
        {rows.map(({ branch, depth }) => {
          const head = headOf.get(branch.id ?? '');
          const note =
            head?.headPersonName &&
            [
              head.familyHead
                ? 'trưởng họ kiêm'
                : head.source === 'MANUAL'
                  ? 'chọn tay'
                  : 'theo dòng',
              head.hasAccount ? null : 'chưa có tài khoản',
            ]
              .filter(Boolean)
              .join(', ');
          return (
            <View
              key={branch.id}
              style={{
                gap: 8,
                borderTopWidth: 1,
                borderColor: colors.border,
                paddingTop: 8,
                paddingLeft: Math.min(depth, 6) * 16,
              }}
            >
              <Text style={[text.label, { color: colors.textPrimary }]}>
                {depth > 0 ? '└ ' : ''}
                {branch.name}
              </Text>
              <Text
                style={[
                  text.caption,
                  head?.headPersonName
                    ? { color: colors.textSecondary }
                    : { color: colors.warning, fontWeight: '600' },
                ]}
              >
                Trưởng chi: {head?.headPersonName ? `${head.headPersonName} (${note})` : 'Chưa có'}
              </Text>
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
                <Button
                  title="Sửa chi"
                  variant="secondary"
                  onPress={() =>
                    stack?.navigate('BranchForm', { familyId, branchId: branch.id ?? undefined })
                  }
                />
                <Button
                  title={head?.headPersonName ? 'Đổi trưởng chi' : 'Chọn trưởng chi'}
                  variant="secondary"
                  onPress={() =>
                    stack?.navigate('PickBranchHead', { familyId, branchId: branch.id ?? '' })
                  }
                />
              </View>
            </View>
          );
        })}
      </Card>
      <Card title="Người trong gia phả chưa có tài khoản">
        {persons.isSuccess && toInvite.length === 0 ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>
            Không có ai cần mời (người còn sống, từ 16 tuổi, chưa có tài khoản).
          </Text>
        ) : null}
        {toInvite.map((p) => (
          <View
            key={p.id}
            style={{
              flexDirection: 'row',
              alignItems: 'center',
              gap: 8,
              borderTopWidth: 1,
              borderColor: colors.border,
              paddingTop: 8,
            }}
          >
            <View style={{ flex: 1 }}>
              <Text style={[text.label, { color: colors.textPrimary }]}>{p.fullName}</Text>
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                {p.branchId ? (branchName(p.branchId) ?? '') : 'Chưa thuộc chi nào'}
              </Text>
            </View>
            <Button
              title="Mời"
              variant="secondary"
              onPress={() => stack?.navigate('Invite', { familyId, personId: p.id! })}
            />
          </View>
        ))}
      </Card>
    </>
  );
}

/** UC-GEN-02, BR-GEN-30 (SCRUM-147): thêm chi hoặc sửa tên, chi cha, người lập chi. Người lập chưa có chi thì vào chi. */
export function BranchFormScreen({ navigation, route }: StackProps<'BranchForm'>) {
  const { familyId, branchId } = route.params;
  const branches = useBranches(familyId);
  const persons = usePeople(familyId);
  if (branches.isPending || persons.isPending) return <Loading />;
  if (branches.isError || persons.isError) {
    return (
      <Screen>
        <ErrorText error={branches.error ?? persons.error} />
      </Screen>
    );
  }
  const list = branches.data.items;
  return (
    <BranchForm
      familyId={familyId}
      branch={list.find((b) => b.id === branchId)}
      branches={list}
      people={persons.data.items}
      onDone={() => navigation.goBack()}
    />
  );
}

function BranchForm({
  familyId,
  branch,
  branches,
  people,
  onDone,
}: {
  familyId: string;
  branch?: Branch;
  branches: Branch[];
  people: Person[];
  onDone: () => void;
}) {
  const cache = useQueryClient();
  const [name, setName] = useState(
    branch?.name ?? `Chi ${branches.filter((b) => !b.parentBranchId).length + 1}`,
  );
  const [parentBranchId, setParentBranchId] = useState(branch?.parentBranchId ?? '');
  const [originPersonId, setOriginPersonId] = useState(branch?.originPersonId ?? '');
  // Người chưa thuộc chi nào lên đầu: thường là con trai của người tổ chưa lập chi.
  const founders = people
    .filter((p) => !p.isDeceased || p.id === originPersonId)
    .sort(
      (a, b) =>
        Number(Boolean(a.branchId)) - Number(Boolean(b.branchId)) ||
        (a.fullName ?? '').localeCompare(b.fullName ?? '', 'vi'),
    );
  const save = useMutation({
    mutationFn: () => {
      const input = {
        name: name.trim(),
        parentBranchId: parentBranchId || null,
        originPersonId: originPersonId || null,
        description: branch?.description ?? null,
      };
      return branch
        ? genealogyManageApi.updateBranch(branch.id!, input)
        : genealogyManageApi.createBranch(familyId, input);
    },
    onSuccess: async () => {
      await Promise.all(
        ['branches', 'branch-heads', 'members', 'tree'].map((key) =>
          cache.invalidateQueries({ queryKey: [key] }),
        ),
      );
      onDone();
    },
  });
  return (
    <Screen>
      <Card
        title={branch ? `Sửa ${branch.name}` : 'Thêm chi'}
        description="Chi gốc do con trai của người tổ lập (Chi 1 con cả, Chi 2 con thứ hai...). Người lập chi chưa thuộc chi nào sẽ vào chi này."
      >
        <TextField label="Tên chi" maxLength={150} value={name} onChangeText={setName} />
        <Select
          label="Thuộc chi"
          value={parentBranchId}
          onChange={setParentBranchId}
          options={[
            { value: '', label: 'Chi gốc (thuộc cả dòng họ)' },
            ...branches
              .filter((b) => b.id !== branch?.id)
              .map((b) => ({ value: b.id ?? '', label: b.name ?? '' })),
          ]}
        />
        <Select
          label="Người lập chi"
          value={originPersonId}
          onChange={setOriginPersonId}
          options={[
            { value: '', label: 'Chưa ghi' },
            ...founders.map((p) => ({
              value: p.id ?? '',
              label: `${p.fullName}${p.branchId ? '' : ' (chưa thuộc chi nào)'}`,
            })),
          ]}
        />
        <ErrorText error={save.error} />
        <Button
          title="Lưu"
          onPress={() => save.mutate()}
          loading={save.isPending}
          disabled={!name.trim()}
        />
      </Card>
    </Screen>
  );
}

/** BR-GEN-29 (SCRUM-146): chọn người trên cây làm trưởng chi, kể cả chưa có tài khoản; hoặc quay về theo dòng. */
export function PickBranchHeadScreen({ navigation, route }: StackProps<'PickBranchHead'>) {
  const { familyId, branchId } = route.params;
  const cache = useQueryClient();
  const branches = useBranches(familyId);
  const persons = usePeople(familyId);
  const heads = useQuery({
    queryKey: ['branch-heads', familyId],
    queryFn: () => genealogyManageApi.branchHeads(familyId),
  });
  const head = heads.data?.find((h) => h.branchId === branchId);
  const [personId, setPersonId] = useState<string | null>(null);
  const current = personId ?? (head?.source === 'MANUAL' ? (head.headPersonId ?? '') : '');
  const branch = branches.data?.items.find((b) => b.id === branchId);
  const options = (persons.data?.items ?? [])
    .filter((p) => !p.isDeceased && p.branchId === branchId)
    .sort((a, b) => (a.fullName ?? '').localeCompare(b.fullName ?? '', 'vi'));
  const save = useMutation({
    mutationFn: () => genealogyManageApi.setBranchHead(branchId, current || null),
    onSuccess: async () => {
      await Promise.all(
        ['branch-heads', 'family-accounts', 'tree'].map((key) =>
          cache.invalidateQueries({ queryKey: [key] }),
        ),
      );
      navigation.goBack();
    },
  });
  if (persons.isPending || heads.isPending) return <Loading />;
  return (
    <Screen>
      <Card
        title={`Trưởng ${branch?.name ?? 'chi'}`}
        description="Mặc định theo dòng (con trưởng của người lập chi). Có thể chọn người khác trong chi, kể cả người chưa có tài khoản."
      >
        <ErrorText error={persons.error ?? heads.error} />
        <Select
          label="Trưởng chi"
          value={current}
          onChange={setPersonId}
          options={[
            { value: '', label: 'Theo dòng (tự động)' },
            ...options.map((p) => ({
              value: p.id ?? '',
              label: `${p.fullName}${p.userId ? '' : ' (chưa có tài khoản)'}`,
            })),
          ]}
        />
        <ErrorText error={save.error} />
        <Button title="Lưu" onPress={() => save.mutate()} loading={save.isPending} />
      </Card>
    </Screen>
  );
}

export function AccountRoleScreen({ navigation, route }: StackProps<'AccountRole'>) {
  const { familyId, account } = route.params;
  const cache = useQueryClient();
  const branches = useBranches(familyId);
  const [role, setRole] = useState<MemberRole>(account.role);
  const [branchId, setBranchId] = useState(account.branchId ?? '');
  const save = useMutation({
    mutationFn: () =>
      familyRolesApi.setRole(familyId, account.userId, { role, branchId: branchId || null }),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['family-accounts'] });
      navigation.goBack();
    },
  });
  return (
    <Screen>
      <Card
        title={`Phân công ${account.fullName}`}
        description="Trưởng chi quản lý chi được chọn và các chi con; chọn cả dòng họ để cùng làm trưởng họ."
      >
        <Select
          label="Vai trò"
          value={role}
          onChange={setRole}
          options={[
            { value: 'MEMBER', label: 'Thành viên' },
            { value: 'BRANCH_ADMIN', label: 'Trưởng họ / Trưởng chi' },
          ]}
        />
        <Select
          label={role === 'BRANCH_ADMIN' ? 'Chi quản lý' : 'Chi của thành viên (không bắt buộc)'}
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
        {branches.isError ? (
          <Text style={[text.caption, { color: colors.error }]}>Không tải được danh sách chi.</Text>
        ) : null}
        <ErrorText error={save.error} />
        <Button title="Lưu" onPress={() => save.mutate()} loading={save.isPending} />
      </Card>
    </Screen>
  );
}

/** Lời mời người chưa có tài khoản: gửi qua tin nhắn, mạng xã hội bằng hộp chia sẻ của máy. */
export function InviteScreen({ route }: StackProps<'Invite'>) {
  const { familyId, personId } = route.params;
  const persons = usePeople(familyId);
  const branches = useBranches(familyId);
  const person = persons.data?.items.find((p) => p.id === personId);
  if (persons.isPending) return <Loading />;
  if (!person) {
    return (
      <Screen>
        <ErrorText error={persons.error ?? new Error('Không tìm thấy người này.')} />
      </Screen>
    );
  }
  const branchName = branches.data?.items.find((b) => b.id === person.branchId)?.name;
  const code = person.branchId ?? familyId; // mã chi tới trưởng chi đó; mã gia đình tới trưởng họ (BR-AUTH-09)
  const reviewer = person.branchId ? `trưởng chi ${branchName ?? ''}`.trim() : 'trưởng họ';
  const message =
    `Mời ${person.fullName} tham gia gia phả trên FamilyConnect.\n` +
    '1. Đăng ký tài khoản FamilyConnect.\n' +
    `2. Vào "Tham gia gia đình", dán mã: ${code}\n` +
    `3. Chờ ${reviewer} duyệt; khi duyệt sẽ gắn tài khoản vào hồ sơ ${person.fullName} trên cây.`;
  return (
    <Screen>
      <Card
        title={`Mời ${person.fullName}`}
        description="Gửi lời mời này qua tin nhắn hoặc mạng xã hội."
      >
        <Text
          selectable
          style={[
            text.body,
            {
              color: colors.textPrimary,
              backgroundColor: colors.surfaceMuted,
              padding: 12,
              borderRadius: radius.control,
            },
          ]}
        >
          {message}
        </Text>
        <Pill label={person.branchId ? `Mã chi ${branchName ?? ''}` : 'Mã gia đình'} />
        <Button title="Gửi lời mời" onPress={() => Share.share({ message })} />
      </Card>
    </Screen>
  );
}
