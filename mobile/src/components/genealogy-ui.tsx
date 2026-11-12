// Component dùng chung cho màn Cây gia phả và Tra cứu xưng hô (UC-GEN-06, 07, 08; SCRUM-90).
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState, type ReactNode } from 'react';
import {
  ActivityIndicator,
  FlatList,
  Image,
  Modal,
  Pressable,
  ScrollView,
  Text,
  View,
} from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { ApiError, API_BASE_URL } from '../services/api';
import { genealogyApi, lifeSpan, type Person } from '../services/genealogy';
import { tokenStorage } from '../services/token-storage';
import { colors, radius, size, spacing, text } from '../theme';
import { EmptyPanel, ErrorPanel } from './community-ui';
import { Button, Loading, TextField } from './ui';

type Gender = 'MALE' | 'FEMALE' | 'OTHER' | null | undefined;

/**
 * Modal là cửa sổ native riêng nên không nhận khoảng đệm của SafeAreaProvider ở App.tsx; trên Android
 * (vẽ tràn màn hình) thanh trạng thái che phần đầu nội dung. Mỗi modal tự đo vùng an toàn của mình.
 */
function ModalSafeArea({
  backgroundColor,
  children,
}: {
  backgroundColor: string;
  children: ReactNode;
}) {
  return (
    <SafeAreaProvider>
      <SafeAreaView edges={['top', 'bottom']} style={{ flex: 1, backgroundColor }}>
        {children}
      </SafeAreaView>
    </SafeAreaProvider>
  );
}

/** Màu giới tính theo Figma "UC-GEN-06 Cây gia phả / 360"; luôn có chú giải bằng chữ đi kèm. */
export function genderColor(gender: Gender): string {
  if (gender === 'MALE') return colors.secondary;
  if (gender === 'FEMALE') return colors.accentPink;
  return colors.borderStrong;
}

export const GENDER_LABEL: Record<'MALE' | 'FEMALE' | 'OTHER', string> = {
  MALE: 'Nam',
  FEMALE: 'Nữ',
  OTHER: 'Khác',
};

/** Ảnh qua API có kiểm quyền (gửi Bearer JWT); không có ảnh hoặc lỗi thì hiện chữ cái đầu của tên. */
export function PersonAvatar({
  name,
  gender,
  mediaId,
  size: box = 38,
}: {
  name?: string | null;
  gender?: Gender;
  mediaId?: string | null;
  size?: number;
}) {
  const [failed, setFailed] = useState(false);
  const access = tokenStorage.getAccess();
  const initial = (name?.trim().split(/\s+/).pop() ?? '?').charAt(0).toUpperCase();
  const shape = { width: box, height: box, borderRadius: box / 2 };
  if (mediaId && access && !failed) {
    return (
      <Image
        accessibilityIgnoresInvertColors
        source={{
          uri: `${API_BASE_URL}/api/v1/media/${encodeURIComponent(mediaId)}/content`,
          headers: { Authorization: `Bearer ${access}` },
        }}
        onError={() => setFailed(true)}
        style={[shape, { backgroundColor: colors.surfaceMuted }]}
      />
    );
  }
  return (
    <View
      importantForAccessibility="no"
      style={[
        shape,
        { backgroundColor: genderColor(gender), alignItems: 'center', justifyContent: 'center' },
      ]}
    >
      <Text style={{ color: colors.surface, fontWeight: '700', fontSize: box * 0.42 }}>
        {initial}
      </Text>
    </View>
  );
}

/** Ô chọn giống select (cao 42): bấm để mở danh sách thành viên. */
export function PersonSelect({
  label,
  value,
  onPress,
}: {
  label: string;
  value?: string | null;
  onPress: () => void;
}) {
  return (
    <View style={{ gap: 6 }}>
      <Text style={[text.label, { color: colors.textPrimary }]}>{label}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}: ${value ?? 'chưa chọn'}`}
        onPress={onPress}
        style={{
          minHeight: size.input,
          borderWidth: 1,
          borderColor: colors.border,
          borderRadius: radius.control,
          backgroundColor: colors.surface,
          paddingHorizontal: 12,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Text
          style={[text.body, { color: value ? colors.textPrimary : colors.textSecondary, flex: 1 }]}
        >
          {value ?? 'Chọn thành viên'}
        </Text>
        <Text style={{ color: colors.textSecondary }}>▾</Text>
      </Pressable>
    </View>
  );
}

/** Tìm và chọn một thành viên trong gia đình (GET /families/{id}/persons?q=). */
export function PersonPicker({
  visible,
  familyId,
  title,
  onClose,
  onPick,
}: {
  visible: boolean;
  familyId: string;
  title: string;
  onClose: () => void;
  onPick: (person: Person) => void;
}) {
  const [input, setInput] = useState('');
  const [q, setQ] = useState('');
  useEffect(() => {
    const timer = setTimeout(() => setQ(input), 300);
    return () => clearTimeout(timer);
  }, [input]);
  const persons = useQuery({
    queryKey: ['persons', familyId, q],
    queryFn: () => genealogyApi.persons(familyId, q),
    enabled: visible,
  });
  const items = persons.data?.items ?? [];
  return (
    <Modal visible={visible} animationType="slide" onRequestClose={onClose}>
      <ModalSafeArea backgroundColor={colors.background}>
        <View style={{ flex: 1, padding: spacing.page, gap: 12 }}>
          <View
            style={{ flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between' }}
          >
            <Text style={[text.sectionTitle, { color: colors.textPrimary, flex: 1 }]}>{title}</Text>
            <Button title="Đóng" variant="secondary" onPress={onClose} />
          </View>
          <TextField
            label="Tìm theo họ tên"
            value={input}
            onChangeText={setInput}
            // API tìm theo họ tên có dấu (PersonRepository); gõ không dấu sẽ không ra.
            placeholder="Gõ có dấu, ví dụ: Nguyễn Văn"
            autoCorrect={false}
          />
          {persons.isLoading ? (
            <Loading />
          ) : persons.isError ? (
            <ErrorPanel error={persons.error} retry={() => persons.refetch()} />
          ) : (
            <FlatList
              data={items}
              keyExtractor={(p) => p.id ?? ''}
              keyboardShouldPersistTaps="handled"
              ListEmptyComponent={<EmptyPanel message="Không tìm thấy thành viên phù hợp." />}
              ListFooterComponent={
                persons.data && persons.data.meta.total > items.length ? (
                  <Text style={[text.caption, { color: colors.textSecondary, padding: 8 }]}>
                    Hiện {items.length}/{persons.data.meta.total} người. Gõ thêm tên để thu hẹp.
                  </Text>
                ) : undefined
              }
              renderItem={({ item }) => (
                <Pressable
                  accessibilityRole="button"
                  onPress={() => onPick(item)}
                  style={({ pressed }) => ({
                    minHeight: 56,
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 12,
                    paddingVertical: 8,
                    borderBottomWidth: 1,
                    borderColor: colors.border,
                    backgroundColor: pressed ? colors.surfaceMuted : 'transparent',
                  })}
                >
                  <PersonAvatar
                    name={item.fullName}
                    gender={item.gender}
                    mediaId={item.avatarMediaId}
                  />
                  <View style={{ flex: 1 }}>
                    <Text style={[text.label, { color: colors.textPrimary }]}>{item.fullName}</Text>
                    <Text style={[text.caption, { color: colors.textSecondary }]}>
                      {lifeSpan(item.birthYear, item.isDeceased, item.deathDate) || ' '}
                    </Text>
                  </View>
                </Pressable>
              )}
            />
          )}
        </View>
      </ModalSafeArea>
    </Modal>
  );
}

export interface Relative {
  id: string;
  name: string;
  /** Ví dụ "Cha", "Mẹ (nuôi)", "Vợ/chồng (đã ly hôn)", "Con". */
  role: string;
}

/** Thẻ chi tiết khi chạm một người trên cây: ảnh, họ tên, năm sinh – mất, nghề, quan hệ trong cây. */
export function PersonDetailSheet({
  personId,
  relatives,
  onClose,
  onOpenPerson,
  onShowBranch,
  onLookup,
}: {
  personId: string | null;
  relatives: Relative[];
  onClose: () => void;
  onOpenPerson: (id: string) => void;
  onShowBranch: (id: string) => void;
  onLookup: (person: Person) => void;
}) {
  const person = useQuery({
    queryKey: ['person', personId],
    queryFn: () => genealogyApi.person(personId as string),
    enabled: Boolean(personId),
  });
  const profession = useQuery({
    queryKey: ['profession-profile', personId],
    // Chưa có hồ sơ nghề (404) hoặc không được xem, ví dụ trẻ dưới 16 tuổi với MEMBER (403): không hiện, không báo lỗi.
    queryFn: () =>
      genealogyApi.professionProfile(personId as string).catch((e: unknown) => {
        if (e instanceof ApiError && (e.status === 404 || e.status === 403)) return null;
        throw e;
      }),
    enabled: Boolean(personId),
  });
  const p = person.data;
  const rows: [string, string | null | undefined][] = p
    ? [
        ['Giới tính', p.gender ? GENDER_LABEL[p.gender] : null],
        ['Năm sinh – mất', lifeSpan(p.birthYear, p.isDeceased, p.deathDate) || null],
        ['Nơi sinh', p.birthplace],
        ['Nơi ở hiện tại', p.currentProvince],
        ['Nơi an táng', p.isDeceased ? p.burialPlace : null],
        [
          'Nghề nghiệp',
          profession.data
            ? [profession.data.jobTitle, profession.data.company, profession.data.workLocation]
                .filter(Boolean)
                .join(' · ')
            : null,
        ],
      ]
    : [];
  return (
    <Modal
      visible={Boolean(personId)}
      animationType="slide"
      presentationStyle="pageSheet"
      onRequestClose={onClose}
    >
      <ModalSafeArea backgroundColor={colors.surface}>
        <ScrollView contentContainerStyle={{ padding: 20, gap: 16 }}>
          {person.isLoading ? (
            <ActivityIndicator color={colors.primary} />
          ) : person.isError ? (
            <ErrorPanel error={person.error} retry={() => person.refetch()} />
          ) : p ? (
            <>
              <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
                <PersonAvatar
                  name={p.fullName}
                  gender={p.gender}
                  mediaId={p.avatarMediaId}
                  size={56}
                />
                <View style={{ flex: 1 }}>
                  <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>
                    {p.fullName}
                  </Text>
                  {p.isDeceased ? (
                    <Text style={[text.caption, { color: colors.textSecondary }]}>Đã mất</Text>
                  ) : null}
                </View>
              </View>
              <View style={{ gap: 8 }}>
                {rows
                  .filter(([, value]) => Boolean(value))
                  .map(([label, value]) => (
                    <View key={label} style={{ flexDirection: 'row', gap: 8 }}>
                      <Text style={[text.label, { color: colors.textSecondary, width: 120 }]}>
                        {label}
                      </Text>
                      <Text style={[text.body, { color: colors.textPrimary, flex: 1 }]}>
                        {value}
                      </Text>
                    </View>
                  ))}
                {profession.isLoading ? (
                  <Text style={[text.caption, { color: colors.textSecondary }]}>
                    Đang tải nghề nghiệp...
                  </Text>
                ) : null}
              </View>
              {relatives.length ? (
                <View style={{ gap: 8 }}>
                  <Text style={[text.label, { color: colors.textPrimary }]}>Quan hệ trong cây</Text>
                  {relatives.map((r) => (
                    <Pressable
                      key={`${r.role}-${r.id}`}
                      accessibilityRole="button"
                      onPress={() => onOpenPerson(r.id)}
                      style={{ minHeight: 44, justifyContent: 'center' }}
                    >
                      <Text style={[text.body, { color: colors.textPrimary }]}>
                        <Text style={{ color: colors.textSecondary }}>{r.role}: </Text>
                        {r.name}
                      </Text>
                    </Pressable>
                  ))}
                </View>
              ) : null}
              <View style={{ gap: 8 }}>
                <Button title="Xem nhánh từ người này" onPress={() => onShowBranch(p.id ?? '')} />
                <Button
                  title="Tra cách xưng hô với người khác"
                  variant="secondary"
                  onPress={() => onLookup(p)}
                />
              </View>
            </>
          ) : null}
          <Button title="Đóng" variant="secondary" onPress={onClose} />
        </ScrollView>
      </ModalSafeArea>
    </Modal>
  );
}
