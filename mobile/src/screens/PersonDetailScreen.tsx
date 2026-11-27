// Thông tin một thành viên khi chạm trên cây: ảnh, họ tên, năm sinh – mất, nghề, quan hệ trong cây (UC-GEN-06; SCRUM-90).
// Là màn của stack (không dùng Modal) để thanh trạng thái Android do app quản lý và có nút quay lại chuẩn.
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ActivityIndicator, Pressable, Text, View } from 'react-native';
import { ErrorPanel, formatWhen } from '../components/community-ui';
import { GENDER_LABEL, PersonAvatar } from '../components/genealogy-ui';
import { Button, Card, Screen } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { communityApi, scopeLabel } from '../services/community';
import { genealogyApi, lifeSpan, type TreeData } from '../services/genealogy';
import { heritageApi } from '../services/heritage';
import { hasBothParents, mergeGraph, relativesOf, spouseRefs } from '../services/genealogy-tree';
import { useFamilyScope } from '../session/FamilyScope';
import { DeathReportBox } from './GenealogyManageScreens';
import { colors, text } from '../theme';

export function PersonDetailScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'PersonDetail'>) {
  const { personId } = route.params;
  const scope = useFamilyScope();
  const familyId = scope.familyId;
  const myUserId = scope.userId;
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
  const education = useQuery({
    queryKey: ['education-profile', personId],
    // Như hồ sơ nghề: chưa có (404) hoặc không được xem (403) thì không hiện (SCRUM-92).
    queryFn: () =>
      heritageApi.educationProfile(personId).catch((e: unknown) => {
        if (e instanceof ApiError && (e.status === 404 || e.status === 403)) return null;
        throw e;
      }),
  });
  // Bài đăng của người đã gắn tài khoản, như trang cá nhân trên web (SCRUM-127).
  const authorId = person.data?.userId ?? null;
  const posts = useQuery({
    queryKey: ['posts', 'author', authorId],
    enabled: Boolean(authorId),
    queryFn: () => communityApi.postsByAuthor(authorId!, 0),
  });
  // Quan hệ lấy từ phần cây đã tải ở màn Cây gia phả (bộ nhớ đệm React Query), không gọi API thêm.
  const trees = queryClient
    .getQueriesData<TreeData>({ queryKey: ['tree', familyId] })
    .map(([, data]) => data);
  const graph = mergeGraph(trees);
  const relatives = relativesOf(graph, personId);
  const spouses = spouseRefs(graph, personId);
  const me = useQuery({ queryKey: ['person', 'me'], queryFn: genealogyApi.myPerson, retry: false });
  // Người tổ là gốc mặc định của cây: thêm con trai cho người tổ thì gợi ý lập chi mới (BR-GEN-30).
  const top = useQuery({
    queryKey: ['tree', familyId, 'top'],
    queryFn: () => genealogyApi.tree(familyId as string, { depth: 1 }),
    enabled: Boolean(familyId) && scope.isAdmin,
  });

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
    [
      'Ngày mất',
      p.isDeceased && p.deathDate
        ? `${p.deathDate.split('-').reverse().join('/')}${p.deathTime ? ` lúc ${p.deathTime}` : ''}`
        : null,
    ],
    ['Nơi an táng', p.isDeceased ? p.burialPlace : null],
    [
      'Nghề nghiệp',
      profession.data
        ? [profession.data.jobTitle, profession.data.company, profession.data.workLocation]
            .filter(Boolean)
            .join(' · ')
        : null,
    ],
    [
      'Học vấn',
      education.data
        ? [
            education.data.school,
            education.data.degree,
            education.data.graduationYear ? `tốt nghiệp ${education.data.graduationYear}` : null,
          ]
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
          {profession.isLoading || education.isLoading ? (
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              Đang tải nghề nghiệp, học vấn...
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
      <DeathReportBox
        person={p}
        onReport={() => navigation.push('DeathReportForm', { personId })}
        onFamilyRoles={() => navigation.popTo('App', { screen: 'FamilyRoles' })}
      />
      {authorId && authorId === myUserId ? (
        // BR-GEN-20: chỉ chính chủ sửa thông tin của mình.
        <Button
          title="Chỉnh sửa hồ sơ"
          onPress={() => navigation.push('EditProfile', { personId })}
        />
      ) : p.canEdit ? (
        // Quản trị sửa hồ sơ chưa có tài khoản (canEdit do backend tính theo BR-GEN-20).
        <Button title="Sửa hồ sơ" onPress={() => navigation.push('PersonForm', { personId })} />
      ) : null}
      {scope.isAdmin ? (
        <Card title="Thêm người thân">
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            {(['child', 'parent', 'spouse'] as const)
              .filter((mode) => mode !== 'parent' || !hasBothParents(graph, personId))
              .map((mode) => (
                <Button
                  key={mode}
                  title={mode === 'child' ? 'Con' : mode === 'parent' ? 'Cha/mẹ' : 'Vợ/chồng'}
                  variant="secondary"
                  onPress={() =>
                    navigation.push('RelativeForm', {
                      personId,
                      mode,
                      spouses,
                      canFoundBranch: top.data?.rootId === personId,
                    })
                  }
                />
              ))}
          </View>
        </Card>
      ) : null}
      {p.canManage ? (
        <Button
          title={p.isDeceased ? 'Sửa thông tin mất / báo nhầm' : 'Báo mất'}
          variant="secondary"
          onPress={() => navigation.push('DeathForm', { personId })}
        />
      ) : null}
      {p.canManage && p.userId && !p.isDeceased ? (
        <Button
          title="Thay tài khoản (mất tài khoản)"
          variant="secondary"
          onPress={() => navigation.push('ReplaceAccount', { personId })}
        />
      ) : null}
      {spouses.length ? (
        <Card title="Hôn nhân">
          {spouses.map((s) => (
            <View key={s.marriageId} style={{ gap: 8 }}>
              <Text style={[text.body, { color: colors.textPrimary }]}>
                {s.name} ·{' '}
                {s.status === 'MARRIED'
                  ? 'đang kết hôn'
                  : s.status === 'DIVORCED'
                    ? 'đã ly hôn'
                    : 'góa'}
              </Text>
              {/* BR-GEN-28: trưởng họ, trưởng chi hoặc chính hai vợ chồng ghi ly hôn. */}
              {s.status === 'MARRIED' &&
              !s.deceased &&
              (scope.isAdmin || personId === me.data?.id || s.id === me.data?.id) ? (
                <Button
                  title="Ghi ly hôn"
                  variant="secondary"
                  onPress={() =>
                    navigation.push('Divorce', {
                      personId,
                      personName: p.fullName ?? '',
                      spouse: s,
                    })
                  }
                />
              ) : null}
            </View>
          ))}
        </Card>
      ) : null}
      {authorId ? (
        <Card title="Bài đăng">
          {posts.isLoading ? <ActivityIndicator color={colors.primary} /> : null}
          {posts.isError ? <ErrorPanel error={posts.error} retry={() => posts.refetch()} /> : null}
          {posts.data && posts.data.items.length === 0 ? (
            <Text style={[text.body, { color: colors.textSecondary }]}>Chưa có bài đăng.</Text>
          ) : null}
          {posts.data?.items.map((post) => (
            <Pressable
              key={post.id}
              accessibilityRole="button"
              accessibilityLabel="Xem bài và bình luận"
              onPress={() => navigation.push('PostDetail', { postId: post.id })}
              style={{ gap: 4, paddingVertical: 8, borderTopWidth: 1, borderColor: colors.border }}
            >
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                {formatWhen(post.createdAt)} · {scopeLabel(post.branchId, new Map(), post.audience)}
              </Text>
              <Text style={[text.body, { color: colors.textPrimary }]} numberOfLines={6}>
                {post.content}
              </Text>
              <Text style={[text.caption, { color: colors.primary }]}>
                {Object.values(post.reactionCounts).reduce((sum, n) => sum + n, 0)} cảm xúc ·{' '}
                {post.commentCount} bình luận · Xem và bình luận
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
