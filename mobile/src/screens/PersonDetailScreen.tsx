// Thông tin một thành viên khi chạm trên cây: ảnh, họ tên, năm sinh – mất, nghề, quan hệ trong cây (UC-GEN-06; SCRUM-90).
// Là màn của stack (không dùng Modal) để thanh trạng thái Android do app quản lý và có nút quay lại chuẩn.
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { ErrorPanel } from '../components/community-ui';
import { GENDER_LABEL, PersonAvatar } from '../components/genealogy-ui';
import { Button, Card, Screen } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { genealogyApi, lifeSpan, type TreeData } from '../services/genealogy';
import { mergeGraph, relativesOf } from '../services/genealogy-tree';
import { useSession } from '../session/SessionContext';
import { colors, text } from '../theme';

export function PersonDetailScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'PersonDetail'>) {
  const { personId } = route.params;
  const { session } = useSession();
  const familyId = session.status === 'signedIn' ? session.membership.familyId : null;
  const queryClient = useQueryClient();
  const person = useQuery({
    queryKey: ['person', personId],
    queryFn: () => genealogyApi.person(personId),
  });
  const profession = useQuery({
    queryKey: ['profession-profile', personId],
    // Chưa có hồ sơ nghề (404) hoặc không được xem, ví dụ trẻ dưới 16 tuổi với MEMBER (403): không hiện, không báo lỗi.
    queryFn: () =>
      genealogyApi.professionProfile(personId).catch((e: unknown) => {
        if (e instanceof ApiError && (e.status === 404 || e.status === 403)) return null;
        throw e;
      }),
  });
  // Quan hệ lấy từ phần cây đã tải ở màn Cây gia phả (bộ nhớ đệm React Query), không gọi API thêm.
  const trees = queryClient
    .getQueriesData<TreeData>({ queryKey: ['tree', familyId] })
    .map(([, data]) => data);
  const relatives = relativesOf(mergeGraph(trees), personId);

  if (person.isLoading) {
    return (
      <Screen>
        <ActivityIndicator color={colors.primary} />
      </Screen>
    );
  }
  if (person.isError || !person.data) {
    return (
      <Screen>
        <ErrorPanel error={person.error} retry={() => person.refetch()} />
      </Screen>
    );
  }
  const p = person.data;
  const pick = () => ({ id: p.id ?? personId, name: p.fullName ?? '', at: Date.now() });
  const rows: [string, string | null | undefined][] = [
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
  ];

  return (
    <Screen>
      <Card>
        <View style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}>
          <PersonAvatar name={p.fullName} gender={p.gender} mediaId={p.avatarMediaId} size={56} />
          <View style={{ flex: 1 }}>
            <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>{p.fullName}</Text>
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
                <Text style={[text.body, { color: colors.textPrimary, flex: 1 }]}>{value}</Text>
              </View>
            ))}
          {profession.isLoading ? (
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              Đang tải nghề nghiệp...
            </Text>
          ) : null}
        </View>
      </Card>
      {relatives.length ? (
        <Card title="Quan hệ trong cây">
          {relatives.map((r) => (
            <Pressable
              key={`${r.role}-${r.id}`}
              accessibilityRole="button"
              onPress={() => navigation.push('PersonDetail', { personId: r.id })}
              style={{ minHeight: 44, justifyContent: 'center' }}
            >
              <Text style={[text.body, { color: colors.textPrimary }]}>
                <Text style={{ color: colors.textSecondary }}>{r.role}: </Text>
                {r.name}
              </Text>
            </Pressable>
          ))}
        </Card>
      ) : null}
      <Button
        title="Xem nhánh từ người này"
        onPress={() => navigation.popTo('App', { screen: 'FamilyTree', params: { root: pick() } })}
      />
      <Button
        title="Tra cách xưng hô với người khác"
        variant="secondary"
        onPress={() =>
          navigation.popTo('App', { screen: 'Relationship', params: { a: pick(), clearB: true } })
        }
      />
    </Screen>
  );
}
