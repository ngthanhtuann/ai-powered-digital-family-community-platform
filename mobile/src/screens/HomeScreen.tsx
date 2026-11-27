// Tổng quan (trang chủ trong menu drawer), cùng nội dung với web app/page.tsx: số liệu nhanh, cây gia phả thu gọn,
// lối vào tra cứu xưng hô, thành viên mới thêm và sự kiện sắp tới. Chỉ dùng API mọi thành viên được gọi.
import type { DrawerScreenProps } from '@react-navigation/drawer';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { Pressable, Text, View } from 'react-native';
import { AdminFamilyPicker } from '../components/AdminFamilyPicker';
import { formatWhen } from '../components/community-ui';
import { ErrorText } from '../components/form-ui';
import { PersonAvatar } from '../components/genealogy-ui';
import { Button, Card, Screen } from '../components/ui';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';
import { api } from '../services/api';
import { communityApi } from '../services/community';
import type { Event } from '../services/events';
import { genealogyApi, genealogyManageApi, type TreeNode } from '../services/genealogy';
import { heritageApi } from '../services/heritage';
import { notificationsApi } from '../services/notifications';
import { useFamilyScope } from '../session/FamilyScope';
import { useSession } from '../session/SessionContext';
import { colors, radius, text } from '../theme';

const ROLE_LABEL: Record<string, string> = {
  SYSTEM_ADMIN: 'Quản trị hệ thống',
  BRANCH_ADMIN: 'Trưởng chi',
  MEMBER: 'Thành viên',
};
const DAY = 24 * 60 * 60 * 1000;
const UPCOMING_DAYS = 90;
const QUICK_TERMS: [string, string, string][] = [
  ['Con với bố mẹ', 'Bố / Mẹ', 'Con'],
  ['Cháu với ông bà', 'Ông / Bà', 'Cháu'],
  ['Em với anh chị', 'Anh / Chị', 'Em'],
];

function Stat({
  label,
  value,
  note,
  failed,
}: {
  label: string;
  value?: number | string;
  note: string;
  failed: boolean;
}) {
  return (
    <View
      style={{
        flexBasis: '47%',
        flexGrow: 1,
        gap: 4,
        padding: 16,
        borderWidth: 1,
        borderColor: colors.border,
        borderRadius: radius.card,
        backgroundColor: colors.surface,
      }}
    >
      <Text style={[text.caption, { color: colors.textSecondary, fontWeight: '500' }]}>
        {label}
      </Text>
      <Text style={{ fontSize: 28, lineHeight: 36, fontWeight: '700', color: colors.textPrimary }}>
        {failed ? '—' : (value ?? '…')}
      </Text>
      <Text style={[text.caption, { color: colors.textSecondary }]}>{note}</Text>
    </View>
  );
}

function Loadable({
  query,
  empty,
  emptyText,
  children,
}: {
  query: { isPending: boolean; isError: boolean; error: unknown };
  empty: boolean;
  emptyText: string;
  children: ReactNode;
}) {
  if (query.isPending)
    return <Text style={[text.body, { color: colors.textSecondary }]}>Đang tải...</Text>;
  if (query.isError) return <ErrorText error={query.error} />;
  if (empty) return <Text style={[text.body, { color: colors.textSecondary }]}>{emptyText}</Text>;
  return <>{children}</>;
}

function TreePerson({ node }: { node: TreeNode }) {
  return (
    <View style={{ width: 96, alignItems: 'center', gap: 4 }}>
      <PersonAvatar
        name={node.fullName}
        gender={node.gender}
        mediaId={node.avatarMediaId}
        size={44}
      />
      <Text style={[text.caption, { color: colors.textPrimary, textAlign: 'center' }]}>
        {node.fullName}
      </Text>
      {node.birthYear ? (
        <Text style={[text.caption, { color: colors.textSecondary }]}>Sinh {node.birthYear}</Text>
      ) : null}
    </View>
  );
}

export function HomeScreen({ navigation: drawer }: DrawerScreenProps<AppDrawerParamList, 'Home'>) {
  const stack = drawer.getParent<NativeStackNavigationProp<RootStackParamList>>();
  const { session } = useSession();
  const scope = useFamilyScope();
  const familyId = scope.familyId ?? undefined;
  const ready = Boolean(familyId);
  // Mốc thời gian cố định trong một lần mở màn (như web).
  const [now] = useState(() => Date.now());
  const from = new Date(now).toISOString();
  const to = new Date(now + UPCOMING_DAYS * DAY).toISOString();
  const events = useQuery({
    queryKey: ['home-events', familyId, from.slice(0, 10)],
    queryFn: () =>
      api.list<Event>('/events', {
        page: 0,
        size: 20,
        sort: 'startAt,asc',
        from,
        to,
        ...(scope.familyParam ? { familyId: scope.familyParam } : {}),
      }),
    enabled: ready,
  });
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: notificationsApi.unreadCount,
    enabled: ready,
  });
  const posts = useQuery({
    queryKey: ['home-posts', familyId],
    queryFn: () => communityApi.posts(0, undefined, scope.familyParam),
    enabled: ready,
  });
  const stories = useQuery({
    queryKey: ['home-stories', familyId],
    queryFn: () => heritageApi.stories(0, scope.familyParam),
    enabled: ready,
  });
  const members = useQuery({
    queryKey: ['home-members', familyId],
    queryFn: () =>
      genealogyManageApi.persons(familyId as string, { page: 0, size: 3, sort: 'createdAt,desc' }),
    enabled: ready,
  });
  const tree = useQuery({
    queryKey: ['tree', familyId, 'home'],
    queryFn: () => genealogyApi.tree(familyId as string, { depth: 2 }),
    enabled: ready,
  });
  if (session.status !== 'signedIn') return null;
  const { user, membership } = session;
  const role = user.roles.includes('SYSTEM_ADMIN') ? 'SYSTEM_ADMIN' : (membership.role ?? 'MEMBER');
  const weekPosts =
    posts.data?.items.filter((p) => now - new Date(p.createdAt).getTime() <= 7 * DAY) ?? [];
  const postsCapped =
    posts.data &&
    weekPosts.length === posts.data.items.length &&
    posts.data.meta.total > weekPosts.length;

  // Cây thu gọn: người gốc, vợ/chồng, tối đa 3 con (như TreePreview của web).
  const nodes = tree.data?.nodes ?? [];
  const edges = tree.data?.edges ?? [];
  const byId = new Map(nodes.map((n) => [n.id, n]));
  const root = byId.get(tree.data?.rootId) ?? nodes[0];
  const spouseEdge = edges.find(
    (e) => e.type === 'MARRIAGE' && (e.source === root?.id || e.target === root?.id),
  );
  const spouse = spouseEdge
    ? byId.get(spouseEdge.source === root?.id ? spouseEdge.target : spouseEdge.source)
    : undefined;
  const kids = edges
    .filter((e) => e.type === 'PARENT_CHILD' && e.source === root?.id)
    .map((e) => byId.get(e.target))
    .filter((n): n is TreeNode => Boolean(n))
    .slice(0, 3);

  return (
    <Screen>
      <Card title={`Xin chào, ${user.fullName}`}>
        {membership.familyName ? (
          <Text style={[text.body, { color: colors.textPrimary }]}>
            Gia đình: {membership.familyName}
          </Text>
        ) : null}
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Vai trò: {ROLE_LABEL[role] ?? role}
        </Text>
      </Card>
      {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
      {!ready ? (
        <Card
          title="Chưa chọn gia đình"
          description="Tài khoản quản trị hệ thống không thuộc gia đình nào. Chọn gia đình ở trên, hoặc mở Thống kê, Quản trị để xem từng gia đình."
        >
          <View />
        </Card>
      ) : (
        <>
          <View
            accessibilityLabel="Số liệu nhanh"
            style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 12 }}
          >
            <Stat
              label="Sự kiện sắp tới"
              value={events.data?.meta.total}
              note={`Trong ${UPCOMING_DAYS} ngày tới`}
              failed={events.isError}
            />
            <Stat
              label="Thông báo chưa đọc"
              value={unread.data}
              note="Mở bằng chuông thông báo"
              failed={unread.isError}
            />
            <Stat
              label="Bài đăng mới"
              value={posts.data ? `${weekPosts.length}${postsCapped ? '+' : ''}` : undefined}
              note="Trong 7 ngày qua"
              failed={posts.isError}
            />
            <Stat
              label="Câu chuyện"
              value={stories.data?.meta.total}
              note="Lịch sử dòng họ"
              failed={stories.isError}
            />
          </View>
          <Card title="Cây gia phả" description="Người gần bạn nhất trong cây">
            <Loadable query={tree} empty={!root} emptyText="Gia đình chưa có thành viên.">
              <View
                style={{
                  backgroundColor: colors.surfaceMuted,
                  borderRadius: radius.card,
                  padding: 12,
                  gap: 8,
                }}
              >
                <View
                  style={{
                    flexDirection: 'row',
                    justifyContent: 'center',
                    alignItems: 'flex-start',
                  }}
                >
                  {root ? <TreePerson node={root} /> : null}
                  {spouse ? (
                    <View
                      style={{
                        width: 20,
                        height: 2,
                        marginTop: 22,
                        backgroundColor: colors.secondary,
                      }}
                    />
                  ) : null}
                  {spouse ? <TreePerson node={spouse} /> : null}
                </View>
                {kids.length ? (
                  <View
                    style={{ flexDirection: 'row', justifyContent: 'center', flexWrap: 'wrap' }}
                  >
                    {kids.map((k) => (
                      <TreePerson key={k.id} node={k} />
                    ))}
                  </View>
                ) : null}
              </View>
            </Loadable>
            <Button
              title="Xem đầy đủ"
              variant="secondary"
              onPress={() => drawer.navigate('FamilyTree')}
            />
          </Card>
          <Card
            title="Tra cứu cách xưng hô"
            description="Chọn hai người trong dòng họ để biết gọi nhau thế nào"
          >
            <Text style={[text.label, { color: colors.textPrimary }]}>Bảng cách xưng hô nhanh</Text>
            <View
              style={{ borderWidth: 1, borderColor: colors.border, borderRadius: radius.control }}
            >
              {[['Quan hệ', 'Gọi là', 'Xưng là'] as [string, string, string], ...QUICK_TERMS].map(
                (row, i) => (
                  <View
                    key={row[0]}
                    style={{
                      flexDirection: 'row',
                      borderTopWidth: i ? 1 : 0,
                      borderColor: colors.border,
                      backgroundColor: i ? colors.surface : colors.background,
                    }}
                  >
                    {row.map((cell, j) => (
                      <Text
                        key={j}
                        style={[
                          text.caption,
                          {
                            flex: 1,
                            padding: 8,
                            color: i && j ? colors.error : colors.textPrimary,
                            fontWeight: i === 0 || j > 0 ? '500' : '400',
                          },
                        ]}
                      >
                        {cell}
                      </Text>
                    ))}
                  </View>
                ),
              )}
            </View>
            <Button title="Tra cứu" onPress={() => drawer.navigate('Relationship')} />
          </Card>
          <Card title="Thành viên mới thêm">
            <Loadable
              query={members}
              empty={!members.data?.items.length}
              emptyText="Chưa có thành viên."
            >
              {members.data?.items.map((p) => (
                <Pressable
                  key={p.id}
                  accessibilityRole="button"
                  onPress={() => stack?.navigate('PersonDetail', { personId: p.id! })}
                  style={{
                    minHeight: 44,
                    flexDirection: 'row',
                    alignItems: 'center',
                    gap: 12,
                    padding: 8,
                    borderRadius: radius.card,
                    backgroundColor: colors.surfaceMuted,
                  }}
                >
                  <PersonAvatar name={p.fullName} gender={p.gender} mediaId={p.avatarMediaId} />
                  <View style={{ flex: 1 }}>
                    <Text style={[text.label, { color: colors.textPrimary }]}>{p.fullName}</Text>
                    <Text style={[text.caption, { color: colors.textSecondary }]}>
                      {[
                        p.birthYear && `Sinh ${p.birthYear}`,
                        p.createdAt &&
                          `Thêm ngày ${new Date(p.createdAt).toLocaleDateString('vi-VN')}`,
                      ]
                        .filter(Boolean)
                        .join(' • ')}
                    </Text>
                  </View>
                </Pressable>
              ))}
            </Loadable>
            <Button
              title="Xem tất cả"
              variant="secondary"
              onPress={() => drawer.navigate('Members')}
            />
          </Card>
          <Card title="Sự kiện và ngày giỗ sắp tới">
            <Loadable
              query={events}
              empty={!events.data?.items.length}
              emptyText={`Không có sự kiện nào trong ${UPCOMING_DAYS} ngày tới.`}
            >
              {events.data?.items.slice(0, 3).map((e) => (
                <Pressable
                  key={e.id}
                  accessibilityRole="button"
                  onPress={() => stack?.navigate('EventDetail', { eventId: e.id })}
                  style={{ minHeight: 44, gap: 2, paddingVertical: 4 }}
                >
                  <Text style={[text.label, { color: colors.textPrimary }]}>{e.title}</Text>
                  <Text style={[text.caption, { color: colors.textSecondary }]}>
                    {formatWhen(e.startAt)}
                    {e.location ? ` · ${e.location}` : ''}
                  </Text>
                </Pressable>
              ))}
            </Loadable>
            <Button
              title="Xem lịch"
              variant="secondary"
              onPress={() => drawer.navigate('Events')}
            />
          </Card>
        </>
      )}
    </Screen>
  );
}
