// Quản lý gia phả trên mobile, cùng nghiệp vụ với các hộp thoại của web (components/genealogy/RelationModal,
// PersonFormModal, PersonPanel, DeathReportBox, admin/ResetCodeModal). Mỗi hộp thoại là một màn của stack.
import { zodResolver } from '@hookform/resolvers/zod';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Controller, useForm, useWatch } from 'react-hook-form';
import { Share, Text, View } from 'react-native';
import { z } from 'zod';
import {
  Checkbox,
  ErrorText,
  Segmented,
  Select,
  TextArea,
  isValidDate,
  todayIso,
  TIME_PATTERN,
} from '../components/form-ui';
import { Button, Card, Loading, Notice, Screen, TextField } from '../components/ui';
import type { RelationMode, RootStackParamList } from '../navigation/types';
import { issuePasswordResetCode } from '../services/auth';
import {
  genealogyApi,
  genealogyManageApi,
  toPersonInput,
  yearOf,
  type Person,
} from '../services/genealogy';
import { useFamilyScope } from '../session/FamilyScope';
import { colors, text } from '../theme';

type Props<K extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList, K>;

const GENDERS = [
  { value: 'MALE' as const, label: 'Nam' },
  { value: 'FEMALE' as const, label: 'Nữ' },
  { value: 'OTHER' as const, label: 'Khác' },
];
const KINDS = [
  { value: 'BIOLOGICAL' as const, label: 'Ruột' },
  { value: 'ADOPTED' as const, label: 'Nuôi' },
  { value: 'STEP' as const, label: 'Kế' },
];
const ROLES = [
  { value: 'FATHER' as const, label: 'Cha' },
  { value: 'MOTHER' as const, label: 'Mẹ' },
];
const TITLES: Record<RelationMode, string> = {
  parent: 'Thêm cha/mẹ cho',
  child: 'Thêm con cho',
  spouse: 'Thêm vợ/chồng cho',
};
const PICK_LABEL: Record<RelationMode, string> = {
  parent: 'Người làm cha/mẹ',
  child: 'Người làm con',
  spouse: 'Người còn lại',
};
const NAME_LABEL: Record<RelationMode, string> = {
  parent: 'Họ tên cha/mẹ',
  child: 'Họ tên người con',
  spouse: 'Họ tên vợ/chồng',
};
const RELATION = { parent: 'PARENT', child: 'CHILD', spouse: 'SPOUSE' } as const;

/** Sau khi lưu: tải lại mọi màn có dữ liệu gia phả (giống invalidateQueries của web). */
function useRefreshGenealogy() {
  const cache = useQueryClient();
  return () =>
    Promise.all(
      [
        'members',
        'tree',
        'person',
        'branches',
        'branch-heads',
        'death-report',
        'family-accounts',
        'posts',
      ].map((key) => cache.invalidateQueries({ queryKey: [key] })),
    );
}

function usePerson(personId: string) {
  return useQuery({ queryKey: ['person', personId], queryFn: () => genealogyApi.person(personId) });
}

const dateField = (label: string) =>
  z.string().refine((v) => v === '' || isValidDate(v), `${label} theo dạng YYYY-MM-DD`);

// SCRUM-143 (BR-GEN-26): người còn sống phải có ngày sinh (BR-GEN-17); người đã mất có thể chỉ biết năm sinh.
const relativeSchema = z
  .object({
    fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(150, 'Tối đa 150 ký tự'),
    gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
    isDeceased: z.boolean(),
    birthDate: dateField('Ngày sinh'),
    birthYear: z.string().refine((v) => v === '' || /^\d{4}$/.test(v), 'Năm gồm 4 chữ số'),
    deathDate: dateField('Ngày mất'),
    parentRole: z.enum(['FATHER', 'MOTHER']),
    kind: z.enum(['BIOLOGICAL', 'ADOPTED', 'STEP']),
    startDate: dateField('Ngày kết hôn'),
  })
  .refine((v) => v.isDeceased || v.birthDate !== '', {
    path: ['birthDate'],
    message: 'Người còn sống phải có ngày sinh',
  });
type RelativeValues = z.infer<typeof relativeSchema>;

/** UC-GEN-04, 05, BR-GEN-26 đến 30: thêm cha/mẹ, con, vợ/chồng; người mới (một lần lưu) hoặc người đã có hồ sơ. */
export function RelativeFormScreen({ navigation, route }: Props<'RelativeForm'>) {
  const { personId, mode, canFoundBranch = false, spouses = [] } = route.params;
  const person = usePerson(personId);
  const [source, setSource] = useState<'new' | 'existing'>('new');
  useEffect(() => {
    if (person.data) navigation.setOptions({ title: `${TITLES[mode]} ${person.data.fullName}` });
  }, [navigation, mode, person.data]);
  if (person.isPending) return <Loading />;
  if (person.isError) {
    return (
      <Screen>
        <ErrorText error={person.error} />
      </Screen>
    );
  }
  return (
    <Screen>
      <Segmented
        label="Nguồn hồ sơ"
        value={source}
        onChange={setSource}
        options={[
          { value: 'new', label: 'Người mới' },
          { value: 'existing', label: 'Người đã có hồ sơ' },
        ]}
      />
      {source === 'new' ? (
        <NewRelativeForm
          person={person.data}
          mode={mode}
          spouses={spouses}
          canFoundBranch={canFoundBranch && mode === 'child'}
          onDone={() => navigation.goBack()}
        />
      ) : (
        <ExistingRelativeForm person={person.data} mode={mode} onDone={() => navigation.goBack()} />
      )}
    </Screen>
  );
}

function NewRelativeForm({
  person,
  mode,
  spouses,
  canFoundBranch,
  onDone,
}: {
  person: Person;
  mode: RelationMode;
  spouses: NonNullable<RootStackParamList['RelativeForm']['spouses']>;
  canFoundBranch: boolean;
  onDone: () => void;
}) {
  const refresh = useRefreshGenealogy();
  const familyId = person.familyId ?? '';
  // BR-GEN-30: con trai của người tổ lập chi mới (tick sẵn); tên thật do server đặt, đây chỉ để hiển thị.
  const [foundBranch, setFoundBranch] = useState(true);
  const branches = useQuery({
    queryKey: ['branches', familyId],
    queryFn: () => genealogyManageApi.branches(familyId),
    enabled: canFoundBranch && Boolean(familyId),
  });
  const nextBranch = `Chi ${(branches.data?.items.filter((b) => !b.parentBranchId).length ?? 0) + 1}`;
  // BR-GEN-27: con mới mặc định có cha/mẹ còn lại là vợ/chồng hiện tại; BR-GEN-28: lấy vợ/chồng mới thì hỏi ghi ly hôn.
  const current = spouses.filter((s) => s.status === 'MARRIED' && !s.deceased);
  const [otherParentId, setOtherParentId] = useState(current[0]?.id ?? '');
  const [divorcePrevious, setDivorcePrevious] = useState(true);
  const defaultGender =
    mode === 'spouse' ? (person.gender === 'FEMALE' ? 'MALE' : 'FEMALE') : 'MALE';
  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<RelativeValues>({
    resolver: zodResolver(relativeSchema),
    defaultValues: {
      fullName: '',
      gender: defaultGender,
      isDeceased: false,
      birthDate: '',
      birthYear: '',
      deathDate: '',
      parentRole: 'FATHER',
      kind: 'BIOLOGICAL',
      startDate: '',
    },
  });
  const gender = useWatch({ control, name: 'gender' });
  const deceased = useWatch({ control, name: 'isDeceased' });
  const birthDate = useWatch({ control, name: 'birthDate' });
  // Vai trò cha/mẹ lấy theo giới tính; chỉ hỏi khi người làm cha/mẹ chọn giới tính Khác.
  const needsRole =
    (mode === 'parent' && gender === 'OTHER') || (mode === 'child' && person.gender === 'OTHER');
  const save = useMutation({
    mutationFn: (v: RelativeValues) =>
      genealogyManageApi.addRelative(person.id!, {
        relation: RELATION[mode],
        person: {
          fullName: v.fullName.trim(),
          gender: v.gender,
          isDeceased: v.isDeceased,
          birthDate: v.birthDate || null,
          birthYear: v.isDeceased && !v.birthDate && v.birthYear ? Number(v.birthYear) : null,
          deathDate: v.isDeceased && v.deathDate ? v.deathDate : null,
        },
        kind: mode === 'spouse' ? null : v.kind,
        parentRole: needsRole ? v.parentRole : null,
        startDate: mode === 'spouse' && v.startDate ? v.startDate : null,
        otherParentId: mode === 'child' && otherParentId ? otherParentId : null,
        divorcePrevious: mode === 'spouse' && current.length > 0 && divorcePrevious,
        foundBranch: canFoundBranch && v.gender === 'MALE' && foundBranch,
      }),
    onSuccess: async () => {
      await refresh();
      onDone();
    },
  });
  const submit = handleSubmit((v) => save.mutate(v));
  return (
    <Card>
      <Controller
        control={control}
        name="fullName"
        render={({ field }) => (
          <TextField
            label={NAME_LABEL[mode]}
            maxLength={150}
            value={field.value}
            onChangeText={field.onChange}
            onBlur={field.onBlur}
            error={errors.fullName?.message}
          />
        )}
      />
      <Controller
        control={control}
        name="gender"
        render={({ field }) => (
          <Select
            label="Giới tính"
            value={field.value}
            options={GENDERS}
            onChange={field.onChange}
          />
        )}
      />
      {needsRole ? (
        <Controller
          control={control}
          name="parentRole"
          render={({ field }) => (
            <Select
              label="Là cha hay mẹ"
              value={field.value}
              options={ROLES}
              onChange={field.onChange}
            />
          )}
        />
      ) : null}
      {mode === 'child' && spouses.length > 0 ? (
        <Select
          label={`${person.gender === 'FEMALE' ? 'Cha' : 'Mẹ'} của người con`}
          value={otherParentId}
          onChange={setOtherParentId}
          options={[
            ...spouses.map((s) => ({
              value: s.id,
              label: `${s.name}${s.status === 'MARRIED' ? '' : ' (đã kết thúc hôn nhân)'}`,
            })),
            { value: '', label: 'Không ghi' },
          ]}
        />
      ) : null}
      {canFoundBranch && gender === 'MALE' ? (
        <Checkbox
          tone="info"
          checked={foundBranch}
          onChange={setFoundBranch}
          label={`Lập ${nextBranch} cho người con này (người lập chi, trưởng chi theo dòng)`}
        />
      ) : null}
      {mode === 'spouse' && current.length > 0 ? (
        <Checkbox
          tone="warning"
          checked={divorcePrevious}
          onChange={setDivorcePrevious}
          label={`Ghi đã ly hôn với ${current.map((s) => s.name).join(', ')}`}
        />
      ) : null}
      <Controller
        control={control}
        name="birthDate"
        render={({ field }) => (
          <TextField
            label="Ngày sinh (YYYY-MM-DD)"
            placeholder="1990-05-20"
            keyboardType="numbers-and-punctuation"
            maxLength={10}
            value={field.value}
            onChangeText={field.onChange}
            error={errors.birthDate?.message}
          />
        )}
      />
      <Controller
        control={control}
        name="isDeceased"
        render={({ field }) => (
          <Checkbox label="Đã mất" checked={field.value} onChange={field.onChange} />
        )}
      />
      {deceased && !birthDate ? (
        <Controller
          control={control}
          name="birthYear"
          render={({ field }) => (
            <TextField
              label="Năm sinh (nếu không rõ ngày)"
              keyboardType="number-pad"
              maxLength={4}
              value={field.value}
              onChangeText={field.onChange}
              error={errors.birthYear?.message}
            />
          )}
        />
      ) : null}
      {deceased ? (
        <Controller
          control={control}
          name="deathDate"
          render={({ field }) => (
            <TextField
              label="Ngày mất (không bắt buộc, YYYY-MM-DD)"
              keyboardType="numbers-and-punctuation"
              maxLength={10}
              value={field.value}
              onChangeText={field.onChange}
              error={errors.deathDate?.message}
            />
          )}
        />
      ) : null}
      {mode !== 'spouse' ? (
        <Controller
          control={control}
          name="kind"
          render={({ field }) => (
            <Select
              label="Loại quan hệ"
              value={field.value}
              options={KINDS}
              onChange={field.onChange}
            />
          )}
        />
      ) : (
        <Controller
          control={control}
          name="startDate"
          render={({ field }) => (
            <TextField
              label="Ngày kết hôn (không bắt buộc, YYYY-MM-DD)"
              keyboardType="numbers-and-punctuation"
              maxLength={10}
              value={field.value}
              onChangeText={field.onChange}
              error={errors.startDate?.message}
            />
          )}
        />
      )}
      <Text style={[text.caption, { color: colors.textSecondary }]}>
        Người mới vào cùng chi với {person.fullName}; sửa thêm quê quán, địa chỉ ở trang hồ sơ sau.
      </Text>
      <ErrorText error={save.error} />
      <Button title="Lưu và vẽ lên cây" onPress={submit} loading={save.isPending} />
    </Card>
  );
}

/** Nối với người đã có hồ sơ: chọn người, vai trò, loại quan hệ; cảnh báo tuổi chênh dưới 15 (BR-GEN-04). */
function ExistingRelativeForm({
  person,
  mode,
  onDone,
}: {
  person: Person;
  mode: RelationMode;
  onDone: () => void;
}) {
  const refresh = useRefreshGenealogy();
  const familyId = person.familyId ?? '';
  const options = useQuery({
    queryKey: ['members-options', familyId],
    queryFn: () =>
      genealogyManageApi.persons(familyId, { page: 0, size: 100, sort: 'fullName,asc' }),
    enabled: Boolean(familyId),
  });
  const [otherId, setOtherId] = useState('');
  // Vai trò mặc định theo giới tính người làm cha/mẹ (BR-GEN-06); người dùng đổi thì giữ lựa chọn cho người đó.
  const [roleChoice, setRoleChoice] = useState<{
    parentId?: string;
    role: 'FATHER' | 'MOTHER';
  } | null>(null);
  const [kind, setKind] = useState<'BIOLOGICAL' | 'ADOPTED' | 'STEP'>('BIOLOGICAL');
  const [startDate, setStartDate] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const others = (options.data?.items ?? []).filter((p) => p.id !== person.id);
  const other = others.find((p) => p.id === otherId);
  // BR-GEN-06: vai trò theo giới tính của người làm cha/mẹ.
  const parent = mode === 'child' ? person : other;
  const role =
    roleChoice && roleChoice.parentId === parent?.id
      ? roleChoice.role
      : parent?.gender === 'FEMALE'
        ? 'MOTHER'
        : 'FATHER';
  const setRole = (value: 'FATHER' | 'MOTHER') =>
    setRoleChoice({ parentId: parent?.id, role: value });
  const [older, younger] = mode === 'child' ? [person, other] : [other, person];
  const olderYear = older ? yearOf(older) : undefined;
  const youngerYear = younger ? yearOf(younger) : undefined;
  const gap = olderYear && youngerYear ? youngerYear - olderYear : undefined;
  const needsConfirm = mode !== 'spouse' && gap !== undefined && gap < 15; // chỉ cảnh báo, server mới chặn
  const badStart = startDate !== '' && !isValidDate(startDate);
  const save = useMutation({
    mutationFn: async () => {
      if (mode === 'spouse') {
        await genealogyManageApi.createMarriage({
          person1Id: person.id!,
          person2Id: otherId,
          startDate: startDate || null,
        });
        return;
      }
      const [childId, parentId] = mode === 'parent' ? [person.id!, otherId] : [otherId, person.id!];
      await genealogyManageApi.addParent(childId, { parentId, parentRole: role, kind });
    },
    onSuccess: async () => {
      await refresh();
      onDone();
    },
  });
  return (
    <Card>
      {options.isPending ? (
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Đang tải danh sách thành viên...
        </Text>
      ) : null}
      <ErrorText error={options.error} />
      <Select
        label={PICK_LABEL[mode]}
        value={otherId}
        onChange={setOtherId}
        placeholder="Chọn thành viên"
        options={others.map((p) => ({
          value: p.id ?? '',
          label: `${p.fullName}${yearOf(p) ? ` (${yearOf(p)})` : ''}`,
        }))}
      />
      {mode !== 'spouse' ? (
        <>
          <Select
            label={`Vai trò${mode === 'child' ? ` của ${person.fullName}` : ''}`}
            value={role}
            options={ROLES}
            onChange={setRole}
          />
          <Select label="Loại quan hệ" value={kind} options={KINDS} onChange={setKind} />
          {needsConfirm ? (
            <Checkbox
              tone="warning"
              checked={confirmed}
              onChange={setConfirmed}
              label={`${
                gap <= 0
                  ? 'Cha/mẹ không lớn tuổi hơn con (hệ thống sẽ từ chối nếu sinh sau con).'
                  : `Cha/mẹ chỉ hơn con ${gap} tuổi.`
              } Tôi xác nhận thông tin đúng.`}
            />
          ) : null}
        </>
      ) : (
        <TextField
          label="Ngày kết hôn (không bắt buộc, YYYY-MM-DD)"
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={startDate}
          onChangeText={setStartDate}
          error={badStart ? 'Ngày theo dạng YYYY-MM-DD' : undefined}
        />
      )}
      <ErrorText error={save.error} />
      <Button
        title="Lưu"
        onPress={() => save.mutate()}
        loading={save.isPending}
        disabled={!otherId || (needsConfirm && !confirmed) || badStart}
      />
    </Card>
  );
}

// UC-GEN-03: như PersonFormModal của web (thêm thành viên, hoặc sửa hồ sơ được phép sửa theo BR-GEN-20).
const personSchema = z
  .object({
    fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(150, 'Tối đa 150 ký tự'),
    gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
    isDeceased: z.boolean(),
    birthDate: dateField('Ngày sinh'),
    birthYear: z.string().refine((v) => v === '' || /^\d{4}$/.test(v), 'Năm gồm 4 chữ số'),
    currentProvince: z.string().trim().max(100, 'Tối đa 100 ký tự'),
    birthplace: z.string().trim().max(255, 'Tối đa 255 ký tự'),
    currentAddress: z.string().trim().max(255, 'Tối đa 255 ký tự'),
    note: z.string().trim().max(2000, 'Tối đa 2000 ký tự'),
  })
  .refine((v) => v.isDeceased || v.birthDate !== '', {
    path: ['birthDate'],
    message: 'Người còn sống phải có ngày sinh',
  });
type PersonValues = z.infer<typeof personSchema>;

export function PersonFormScreen({ navigation, route }: Props<'PersonForm'>) {
  const personId = route.params.personId;
  const scope = useFamilyScope();
  const existing = useQuery({
    queryKey: ['person', personId],
    queryFn: () => genealogyApi.person(personId!),
    enabled: Boolean(personId),
  });
  useEffect(() => {
    navigation.setOptions({ title: personId ? 'Sửa thành viên' : 'Thêm thành viên' });
  }, [navigation, personId]);
  if (personId && existing.isPending) return <Loading />;
  if (personId && existing.isError) {
    return (
      <Screen>
        <ErrorText error={existing.error} />
      </Screen>
    );
  }
  return (
    <PersonForm
      familyId={existing.data?.familyId ?? scope.familyId ?? ''}
      person={existing.data}
      onDone={() => navigation.goBack()}
    />
  );
}

function PersonForm({
  familyId,
  person,
  onDone,
}: {
  familyId: string;
  person?: Person;
  onDone: () => void;
}) {
  const refresh = useRefreshGenealogy();
  const {
    control,
    handleSubmit,
    formState: { errors },
  } = useForm<PersonValues>({
    resolver: zodResolver(personSchema),
    defaultValues: {
      fullName: person?.fullName ?? '',
      gender: person?.gender ?? 'MALE',
      isDeceased: person?.isDeceased ?? false,
      birthDate: person?.birthDate ?? '',
      birthYear: person?.birthYear ? String(person.birthYear) : '',
      currentProvince: person?.currentProvince ?? '',
      birthplace: person?.birthplace ?? '',
      currentAddress: person?.currentAddress ?? '',
      note: person?.note ?? '',
    },
  });
  const formDeceased = useWatch({ control, name: 'isDeceased' });
  const formBirthDate = useWatch({ control, name: 'birthDate' });
  const save = useMutation({
    mutationFn: (v: PersonValues) => {
      const input = {
        ...(person ? toPersonInput(person) : { isDeceased: false }),
        fullName: v.fullName,
        gender: v.gender,
        isDeceased: v.isDeceased,
        birthDate: v.birthDate || null,
        birthYear: !v.birthDate && v.birthYear ? Number(v.birthYear) : null,
        currentProvince: v.currentProvince || null,
        birthplace: v.birthplace || null,
        currentAddress: v.currentAddress || null,
        note: v.note || null,
      };
      return person?.id
        ? genealogyApi.updatePerson(person.id, input)
        : genealogyManageApi.createPerson(familyId, input);
    },
    onSuccess: async () => {
      await refresh();
      onDone();
    },
  });
  const field = (
    name: 'fullName' | 'currentProvince' | 'birthplace' | 'currentAddress',
    label: string,
    max: number,
  ) => (
    <Controller
      control={control}
      name={name}
      render={({ field: f }) => (
        <TextField
          label={label}
          maxLength={max}
          value={f.value}
          onChangeText={f.onChange}
          onBlur={f.onBlur}
          error={errors[name]?.message}
        />
      )}
    />
  );
  return (
    <Screen>
      <Card>
        {field('fullName', 'Họ tên', 150)}
        <Controller
          control={control}
          name="gender"
          render={({ field: f }) => (
            <Select label="Giới tính" value={f.value} options={GENDERS} onChange={f.onChange} />
          )}
        />
        <Controller
          control={control}
          name="isDeceased"
          render={({ field: f }) => (
            <Checkbox label="Đã mất" checked={f.value} onChange={f.onChange} />
          )}
        />
        <Controller
          control={control}
          name="birthDate"
          render={({ field: f }) => (
            <TextField
              label="Ngày sinh (YYYY-MM-DD)"
              placeholder="1990-05-20"
              keyboardType="numbers-and-punctuation"
              maxLength={10}
              value={f.value}
              onChangeText={f.onChange}
              error={errors.birthDate?.message}
            />
          )}
        />
        {formDeceased && !formBirthDate ? (
          <Controller
            control={control}
            name="birthYear"
            render={({ field: f }) => (
              <TextField
                label="Năm sinh (nếu không rõ ngày)"
                keyboardType="number-pad"
                maxLength={4}
                value={f.value}
                onChangeText={f.onChange}
                error={errors.birthYear?.message}
              />
            )}
          />
        ) : null}
        {field('currentProvince', 'Tỉnh/thành đang sống', 100)}
        {field('birthplace', 'Quê quán', 255)}
        {field('currentAddress', 'Địa chỉ', 255)}
        <Controller
          control={control}
          name="note"
          render={({ field: f }) => (
            <TextArea
              label="Giới thiệu"
              maxLength={2000}
              value={f.value}
              onChangeText={f.onChange}
              error={errors.note?.message}
            />
          )}
        />
        <ErrorText error={save.error} />
        <Button
          title="Lưu"
          onPress={handleSubmit((v) => save.mutate(v))}
          loading={save.isPending}
        />
      </Card>
    </Screen>
  );
}

/**
 * BR-GEN-21 (SCRUM-134): người quản lý chi báo mất trực tiếp (kể cả hồ sơ đã gắn tài khoản), sửa ngày giờ mất,
 * hoặc khôi phục còn sống khi báo nhầm. Máy chủ khóa tài khoản và gửi thông báo tang.
 */
export function DeathFormScreen({ navigation, route }: Props<'DeathForm'>) {
  const person = usePerson(route.params.personId);
  if (person.isPending) return <Loading />;
  if (person.isError) {
    return (
      <Screen>
        <ErrorText error={person.error} />
      </Screen>
    );
  }
  return <DeathForm person={person.data} onDone={() => navigation.goBack()} />;
}

function DeathForm({ person, onDone }: { person: Person; onDone: () => void }) {
  const refresh = useRefreshGenealogy();
  const [deathDate, setDeathDate] = useState(person.deathDate ?? '');
  const [deathTime, setDeathTime] = useState(person.deathTime ?? '');
  const [burialPlace, setBurialPlace] = useState(person.burialPlace ?? '');
  const [mistake, setMistake] = useState(false);
  const today = todayIso();
  const dateError =
    deathDate && (!isValidDate(deathDate) || deathDate > today)
      ? 'Ngày mất hợp lệ, không sau hôm nay'
      : undefined;
  const timeError = deathTime && !TIME_PATTERN.test(deathTime) ? 'Giờ theo dạng HH:mm' : undefined;
  const save = useMutation({
    mutationFn: (input: Parameters<typeof genealogyManageApi.setDeath>[1]) =>
      genealogyManageApi.setDeath(person.id!, input),
    onSuccess: async () => {
      await refresh();
      onDone();
    },
  });
  return (
    <Screen>
      <Card
        title={
          person.isDeceased ? `Thông tin mất của ${person.fullName}` : `Báo mất ${person.fullName}`
        }
      >
        {!person.isDeceased ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>
            Hồ sơ chuyển sang đã mất. Tài khoản gắn với hồ sơ (nếu có) bị khóa; người trong chi và
            trưởng họ nhận thông báo tang.
          </Text>
        ) : null}
        <TextField
          label="Ngày mất (YYYY-MM-DD)"
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={deathDate}
          onChangeText={setDeathDate}
          error={dateError}
        />
        <TextField
          label="Giờ mất (không bắt buộc, HH:mm)"
          keyboardType="numbers-and-punctuation"
          maxLength={5}
          value={deathTime}
          onChangeText={setDeathTime}
          error={timeError}
        />
        <TextField
          label="Nơi an táng (không bắt buộc)"
          maxLength={255}
          value={burialPlace}
          onChangeText={setBurialPlace}
        />
        <ErrorText error={save.error} />
        <Button
          title={person.isDeceased ? 'Lưu' : 'Xác nhận đã mất'}
          loading={save.isPending}
          disabled={!deathDate || Boolean(dateError) || Boolean(timeError)}
          onPress={() =>
            save.mutate({
              isDeceased: true,
              deathDate,
              deathTime: deathTime || null,
              burialPlace: burialPlace.trim() || null,
            })
          }
        />
        {person.isDeceased ? (
          <View style={{ gap: 8, borderTopWidth: 1, borderColor: colors.border, paddingTop: 16 }}>
            <Checkbox
              tone="warning"
              checked={mistake}
              onChange={setMistake}
              label="Báo nhầm: người này vẫn còn sống"
            />
            <Button
              title="Khôi phục còn sống"
              variant="secondary"
              disabled={!mistake}
              loading={save.isPending}
              onPress={() => save.mutate({ isDeceased: false })}
            />
          </View>
        ) : null}
      </Card>
    </Screen>
  );
}

/** BR-GEN-24 (SCRUM-135): con ruột gửi báo mất; cần một người khác xác nhận trong 7 ngày. */
export function DeathReportFormScreen({ navigation, route }: Props<'DeathReportForm'>) {
  const person = usePerson(route.params.personId);
  const refresh = useRefreshGenealogy();
  const [deathDate, setDeathDate] = useState('');
  const [deathTime, setDeathTime] = useState('');
  const [burialPlace, setBurialPlace] = useState('');
  const today = todayIso();
  const dateError =
    deathDate && (!isValidDate(deathDate) || deathDate > today)
      ? 'Ngày mất hợp lệ, không sau hôm nay'
      : undefined;
  const timeError = deathTime && !TIME_PATTERN.test(deathTime) ? 'Giờ theo dạng HH:mm' : undefined;
  const save = useMutation({
    mutationFn: () =>
      genealogyManageApi.reportDeath(route.params.personId, {
        deathDate,
        deathTime: deathTime || null,
        burialPlace: burialPlace.trim() || null,
      }),
    onSuccess: async () => {
      await refresh();
      navigation.goBack();
    },
  });
  return (
    <Screen>
      <Card title={`Báo mất ${person.data?.fullName ?? ''}`}>
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Báo mất cần một người xác nhận (con ruột khác, hoặc trưởng chi, trưởng họ) trong 7 ngày
          rồi mới chuyển hồ sơ sang đã mất.
        </Text>
        <TextField
          label="Ngày mất (YYYY-MM-DD)"
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={deathDate}
          onChangeText={setDeathDate}
          error={dateError}
        />
        <TextField
          label="Giờ mất (không bắt buộc, HH:mm)"
          keyboardType="numbers-and-punctuation"
          maxLength={5}
          value={deathTime}
          onChangeText={setDeathTime}
          error={timeError}
        />
        <TextField
          label="Nơi an táng (không bắt buộc)"
          maxLength={255}
          value={burialPlace}
          onChangeText={setBurialPlace}
        />
        <ErrorText error={save.error} />
        <Button
          title="Gửi báo mất"
          onPress={() => save.mutate()}
          loading={save.isPending}
          disabled={!deathDate || Boolean(dateError) || Boolean(timeError)}
        />
      </Card>
    </Screen>
  );
}

/** BR-GEN-28 (SCRUM-145): ghi đã ly hôn; vợ/chồng ngoài tộc sẽ mờ trên cây và bị ẩn với thành viên. */
export function DivorceScreen({ navigation, route }: Props<'Divorce'>) {
  const { personName, spouse } = route.params;
  const refresh = useRefreshGenealogy();
  const today = todayIso();
  const [endDate, setEndDate] = useState(today);
  const [sure, setSure] = useState(false);
  const dateError =
    !isValidDate(endDate) || endDate > today ? 'Ngày ly hôn hợp lệ, không sau hôm nay' : undefined;
  const save = useMutation({
    mutationFn: () =>
      genealogyManageApi.updateMarriage(spouse.marriageId, { status: 'DIVORCED', endDate }),
    onSuccess: async () => {
      await refresh();
      navigation.goBack();
    },
  });
  return (
    <Screen>
      <Card title={`Ghi ly hôn: ${personName} và ${spouse.name}`}>
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Người ngoài tộc sau khi ly hôn sẽ mờ trên cây, thành viên không mở được hồ sơ và bài viết
          của họ; tài khoản của họ (nếu có) rời dòng họ. Trưởng họ, trưởng chi vẫn xem được.
        </Text>
        <TextField
          label="Ngày ly hôn (YYYY-MM-DD)"
          keyboardType="numbers-and-punctuation"
          maxLength={10}
          value={endDate}
          onChangeText={setEndDate}
          error={dateError}
        />
        <Checkbox
          tone="warning"
          checked={sure}
          onChange={setSure}
          label="Tôi xác nhận hai người đã ly hôn"
        />
        <ErrorText error={save.error} />
        <Button
          title="Ghi ly hôn"
          onPress={() => save.mutate()}
          loading={save.isPending}
          disabled={!sure || Boolean(dateError)}
        />
      </Card>
    </Screen>
  );
}

/**
 * BR-GEN-23 (SCRUM-140): người thân mất tài khoản đăng ký tài khoản mới và được duyệt vào dòng họ, rồi người quản lý
 * gắn tài khoản mới vào hồ sơ. Tài khoản cũ bị khóa (không xóa); vai trò và nội dung công khai chuyển sang.
 */
export function ReplaceAccountScreen({ navigation, route }: Props<'ReplaceAccount'>) {
  const { personId } = route.params;
  const person = usePerson(personId);
  const refresh = useRefreshGenealogy();
  const [newUserId, setNewUserId] = useState('');
  const [reason, setReason] = useState('');
  const candidates = useQuery({
    queryKey: ['account-candidates', personId],
    queryFn: () => genealogyManageApi.accountCandidates(personId),
  });
  const save = useMutation({
    mutationFn: () =>
      genealogyManageApi.replaceAccount(personId, { newUserId, reason: reason.trim() }),
    onSuccess: async () => {
      await refresh();
      navigation.goBack();
    },
  });
  return (
    <Screen>
      <Card title={`Thay tài khoản cho ${person.data?.fullName ?? ''}`}>
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Dùng khi người này mất tài khoản cũ. Trước tiên họ đăng ký tài khoản mới và được duyệt vào
          dòng họ. Tài khoản cũ sẽ bị khóa (không xóa); vai trò trưởng họ/chi, bài đăng, bình luận,
          sự kiện, tài liệu chuyển sang tài khoản mới.
        </Text>
        {candidates.isPending ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>
            Đang tải danh sách tài khoản...
          </Text>
        ) : null}
        <ErrorText error={candidates.error} />
        <Select
          label="Tài khoản mới"
          value={newUserId}
          onChange={setNewUserId}
          placeholder={
            candidates.data?.length ? 'Chọn tài khoản' : 'Chưa có tài khoản nào chưa gắn hồ sơ'
          }
          options={(candidates.data ?? []).map((c) => ({
            value: c.userId ?? '',
            label: c.fullName ?? '',
          }))}
        />
        <TextField
          label="Lý do"
          maxLength={255}
          value={reason}
          onChangeText={setReason}
          placeholder="Ví dụ: mất điện thoại và email cũ"
        />
        <ErrorText error={save.error} />
        <Button
          title="Thay tài khoản"
          onPress={() => save.mutate()}
          loading={save.isPending}
          disabled={!newUserId || !reason.trim()}
        />
      </Card>
    </Screen>
  );
}

/** UC-AUTH-15: trưởng họ hoặc quản trị cấp mã đặt lại mật khẩu; mã chỉ hiện một lần ở màn này. */
export function ResetCodeScreen({ route }: Props<'ResetCode'>) {
  const { userId, fullName } = route.params;
  const issue = useMutation({ mutationFn: () => issuePasswordResetCode(userId) });
  const data = issue.data;
  return (
    <Screen>
      <Card
        title={`Đặt lại mật khẩu cho ${fullName}`}
        description="Dùng khi người thân quên mật khẩu. Mã dùng một lần, hết hạn sau 30 phút; cấp mã mới thì mã cũ hết hiệu lực."
      >
        {!data ? (
          <Button title="Tạo mã" loading={issue.isPending} onPress={() => issue.mutate()} />
        ) : null}
        <ErrorText error={issue.error} />
        {data ? (
          <>
            <Text
              selectable
              accessibilityLabel="Mã đặt lại mật khẩu"
              style={[
                text.pageTitle,
                {
                  color: colors.textPrimary,
                  textAlign: 'center',
                  letterSpacing: 4,
                  backgroundColor: colors.surfaceMuted,
                  padding: 16,
                },
              ]}
            >
              {data.code}
            </Text>
            <Text style={[text.body, { color: colors.textSecondary }]}>
              Hết hạn lúc{' '}
              {new Date(data.expiresAt).toLocaleTimeString('vi-VN', {
                hour: '2-digit',
                minute: '2-digit',
              })}
              . Mã chỉ hiện một lần: gửi riêng cho {fullName}. Người đó vào màn đăng nhập, chọn
              &quot;Quên mật khẩu?&quot;, nhập email, mã và mật khẩu mới.
            </Text>
            <Button
              title="Gửi mã"
              variant="secondary"
              onPress={() => Share.share({ message: data.code })}
            />
          </>
        ) : null}
      </Card>
    </Screen>
  );
}

/** BR-GEN-24 trên màn thông tin thành viên: báo mất đang chờ xác nhận, gợi ý người kế nhiệm, nút báo mất cho con ruột. */
export function DeathReportBox({
  person,
  onReport,
  onFamilyRoles,
}: {
  person: Person;
  onReport: () => void;
  onFamilyRoles: () => void;
}) {
  const refresh = useRefreshGenealogy();
  const report = useQuery({
    queryKey: ['death-report', person.id],
    queryFn: () => genealogyManageApi.latestDeathReport(person.id!),
  });
  const confirm = useMutation({
    mutationFn: (id: string) => genealogyManageApi.confirmDeathReport(id),
    onSuccess: refresh,
  });
  const r = report.data;
  if (report.isPending) return null;
  if (report.isError) return <ErrorText error={report.error} />;
  const day = (v?: string | null) => (v ? v.slice(0, 10).split('-').reverse().join('/') : '');
  return (
    <>
      {r?.status === 'PENDING' ? (
        <Notice
          variant="warning"
          title={`${r.reportedByName ?? 'Một người con'} báo ${r.personName} đã mất ngày ${day(r.deathDate)}${
            r.deathTime ? ` lúc ${r.deathTime}` : ''
          }.`}
          message={`Đang chờ xác nhận đến ${r.expiresAt ? new Date(r.expiresAt).toLocaleString('vi-VN') : ''}; quá hạn không ai xác nhận thì báo mất tự hủy.`}
        />
      ) : null}
      {r?.status === 'PENDING' && r.canConfirm ? (
        <Button
          title="Xác nhận đã mất"
          loading={confirm.isPending}
          onPress={() => confirm.mutate(r.id!)}
        />
      ) : null}
      <ErrorText error={confirm.error} />
      {r?.status === 'CONFIRMED' && r.successorName ? (
        <Card title={`Gợi ý người kế nhiệm: ${r.successorName}`}>
          <Text style={[text.body, { color: colors.textSecondary }]}>
            Theo thứ tự con trai trưởng; con trưởng mất trước thì cháu đích tôn; không có con trai
            thì em trai kế tiếp.
            {!r.successorHasAccount
              ? ` ${r.successorName} chưa có tài khoản: hãy mời tham gia trước.`
              : ''}{' '}
            Trưởng họ quyết định ở Phân công trưởng chi.
          </Text>
          <Button title="Mở Phân công trưởng chi" variant="secondary" onPress={onFamilyRoles} />
        </Card>
      ) : null}
      {!r && person.canReportDeath ? (
        <Button title="Báo mất" variant="secondary" onPress={onReport} />
      ) : null}
    </>
  );
}
