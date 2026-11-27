// UC-GEN-06 trên mobile (SCRUM-90): cây gia phả dạng danh sách theo đời, mở/thu nhánh, tải nhánh khi cần.
// Chạm một người để xem chi tiết; người quản lý thêm người thân, báo mất, ghi ly hôn ở màn chi tiết (như PersonPanel của web).
import type { DrawerScreenProps } from '@react-navigation/drawer';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQueries, useQuery } from '@tanstack/react-query';
import { useCallback, useMemo, useState } from 'react';
import { ActivityIndicator, FlatList, Pressable, Text, View } from 'react-native';
import { EmptyPanel, ErrorPanel } from '../components/community-ui';
import { PersonAvatar, genderColor } from '../components/genealogy-ui';
import { AdminFamilyPicker } from '../components/AdminFamilyPicker';
import { Button, Loading, TextField } from '../components/ui';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';
import { genealogyApi, genealogyManageApi, lifeSpan } from '../services/genealogy';
import {
  mergeGraph,
  spouseLabels,
  visibleRows,
  type Graph,
  type TreeRow,
} from '../services/genealogy-tree';
import { useFamilyScope } from '../session/FamilyScope';
import { colors, radius, spacing, text } from '../theme';

/** Số đời mỗi lần tải (genealogy.yaml mặc định 3); đủ để mở 2 tầng mà không phải chờ. */
const DEPTH = 3;
const INDENT = 16;
/** Thụt lề tối đa để tên vẫn đọc được ở cỡ 360. */
const MAX_INDENT_LEVEL = 6;

export function FamilyTreeScreen({
  navigation: drawer,
  route,
}: DrawerScreenProps<AppDrawerParamList, 'FamilyTree'>) {
  // Thông tin thành viên, Chọn thành viên là màn của stack gốc (không dùng Modal, SCRUM-90).
  const stack = drawer.getParent<NativeStackNavigationProp<RootStackParamList>>();
  const scope = useFamilyScope();
  const familyId = scope.familyId;
  const familyName = scope.familyName;
  // SCRUM-143: tìm người theo tên trong dòng họ, chọn thì cây mở từ người đó.
  const [q, setQ] = useState('');
  const term = q.trim();
  const found = useQuery({
    queryKey: ['tree-search', familyId, term],
    queryFn: () =>
      genealogyManageApi.persons(familyId as string, {
        q: term,
        page: 0,
        size: 8,
        sort: 'fullName,asc',
      }),
    enabled: Boolean(familyId) && term.length >= 2,
  });
  // Ô của chính mình được tô màu và gắn nhãn "Bạn" như web; chưa gắn hồ sơ thì /persons/me trả 404.
  const me = useQuery({ queryKey: ['person', 'me'], queryFn: genealogyApi.myPerson, retry: false });
  // undefined: gốc mặc định của API là người tổ (genealogy.yaml, 17/11/2026).
  const [rootId, setRootId] = useState<string | undefined>();
  const [branchRoots, setBranchRoots] = useState<string[]>([]);
  // Nhánh đang mở, gắn với gốc đang xem: đổi gốc thì quay về chỉ mở người gốc.
  const [open, setOpen] = useState<{ root?: string; ids: Set<string> }>({ ids: new Set() });
  // Gốc mới từ màn Chọn thành viên hoặc "Xem nhánh từ người này": áp dụng một lần cho mỗi lần chọn (theo `at`).
  const [appliedRootAt, setAppliedRootAt] = useState<number | undefined>();
  const rootParam = route.params?.root;
  if (rootParam && rootParam.at !== appliedRootAt) {
    setAppliedRootAt(rootParam.at);
    setRootId(rootParam.id);
    setBranchRoots([]);
  }

  const first = useQuery({
    queryKey: ['tree', familyId, rootId ?? 'default'],
    queryFn: () => genealogyApi.tree(familyId as string, rootId ? { rootId, depth: DEPTH } : {}),
    enabled: Boolean(familyId),
  });
  const branches = useQueries({
    queries: branchRoots.map((id) => ({
      queryKey: ['tree', familyId, id, DEPTH],
      queryFn: () => genealogyApi.tree(familyId as string, { rootId: id, depth: DEPTH }),
    })),
    // combine được TanStack chia sẻ cấu trúc nên kết quả giữ nguyên tham chiếu khi dữ liệu không đổi.
    combine: (results) => ({
      data: results.map((r) => r.data),
      loading: results.map((r) => r.isLoading),
      error: results.find((r) => r.isError),
    }),
  });
  const graph: Graph = useMemo(
    () => mergeGraph([first.data, ...branches.data]),
    [first.data, branches.data],
  );
  const shownRoot = first.data?.rootId;

  const expanded = useMemo(
    () => (open.root === shownRoot ? open.ids : new Set(shownRoot ? [shownRoot] : [])),
    [open, shownRoot],
  );
  const rows = useMemo(() => visibleRows(graph, shownRoot, expanded), [graph, shownRoot, expanded]);
  const loadingBranch = useMemo(
    () => new Set(branchRoots.filter((_, i) => branches.loading[i])),
    [branchRoots, branches.loading],
  );
  const branchError = branches.error;

  const toggle = useCallback(
    (id: string) => {
      const next = new Set(expanded);
      if (graph.nodes.get(id)?.hasMoreChildren && !branchRoots.includes(id)) {
        setBranchRoots((roots) => [...roots, id]); // tải nhánh lười rồi mở luôn
        next.add(id);
      } else if (next.has(id)) next.delete(id);
      else next.add(id);
      setOpen({ root: shownRoot, ids: next });
    },
    [graph, branchRoots, expanded, shownRoot],
  );

  const resetRoot = () => {
    setRootId(undefined);
    setBranchRoots([]);
  };
  const openPerson = (personId: string) => stack?.navigate('PersonDetail', { personId });

  if (!familyId) {
    return scope.isSystemAdmin ? (
      <View style={{ padding: spacing.page, gap: 12 }}>
        <AdminFamilyPicker />
        <EmptyPanel message="Chọn gia đình để xem cây gia phả." />
      </View>
    ) : (
      <EmptyPanel message="Bạn chưa thuộc gia đình nào nên chưa có cây gia phả." />
    );
  }
  if (first.isLoading) return <Loading label="Đang tải cây gia phả..." />;
  if (first.isError) return <ErrorPanel error={first.error} retry={() => first.refetch()} />;

  const header = (
    <View style={{ gap: 12, paddingBottom: 8 }}>
      <View style={{ gap: 2 }}>
        <Text style={[text.pageTitle, { color: colors.textPrimary }]}>Cây gia phả</Text>
        {familyName ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>{familyName}</Text>
        ) : null}
      </View>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button
          title="Chọn người làm gốc"
          variant="secondary"
          onPress={() =>
            stack?.navigate('PersonPicker', { title: 'Chọn người làm gốc cây', target: 'treeRoot' })
          }
        />
        {rootId ? <Button title="Về gốc mặc định" variant="secondary" onPress={resetRoot} /> : null}
        <Button title="Tra cứu xưng hô" onPress={() => drawer.navigate('Relationship')} />
      </View>
      {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
      <TextField
        label="Tìm người trên cây"
        placeholder="Gõ ít nhất 2 chữ của họ tên"
        value={q}
        onChangeText={setQ}
        autoCorrect={false}
      />
      {term.length >= 2 ? (
        <View
          accessibilityLabel="Kết quả tìm"
          style={{
            borderWidth: 1,
            borderColor: colors.border,
            borderRadius: radius.card,
            backgroundColor: colors.surface,
          }}
        >
          {found.isPending ? (
            <Text style={[text.body, { color: colors.textSecondary, padding: 12 }]}>
              Đang tìm...
            </Text>
          ) : null}
          {found.isError ? (
            <Text
              accessibilityRole="alert"
              style={[text.body, { color: colors.error, padding: 12 }]}
            >
              Không tìm được, vui lòng thử lại.
            </Text>
          ) : null}
          {found.data?.items.length === 0 ? (
            <Text style={[text.body, { color: colors.textSecondary, padding: 12 }]}>
              Không thấy ai có tên như vậy.
            </Text>
          ) : null}
          {found.data?.items.map((p) => (
            <Pressable
              key={p.id}
              accessibilityRole="button"
              onPress={() => {
                setQ('');
                setRootId(p.id ?? undefined);
                setBranchRoots([]);
              }}
              style={{ minHeight: 44, justifyContent: 'center', paddingHorizontal: 12 }}
            >
              <Text style={[text.body, { color: colors.textPrimary }]}>
                {p.fullName}
                {p.birthYear ? ` (${p.birthYear})` : ''}
                {p.isDeceased ? ' · đã mất' : ''}
              </Text>
            </Pressable>
          ))}
        </View>
      ) : null}
      <Legend />
      {branchError ? (
        <ErrorPanel error={branchError.error} retry={() => branchError.refetch()} />
      ) : null}
    </View>
  );

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <FlatList
        data={rows}
        keyExtractor={(row) => row.id}
        ListHeaderComponent={header}
        ListEmptyComponent={<EmptyPanel message="Gia đình chưa có thành viên nào trong cây." />}
        contentContainerStyle={{ padding: spacing.page, paddingBottom: 32 }}
        initialNumToRender={30}
        windowSize={11}
        refreshing={first.isRefetching}
        onRefresh={() => first.refetch()}
        renderItem={({ item }) => (
          <TreeRowItem
            row={item}
            graph={graph}
            expanded={expanded.has(item.id)}
            loading={loadingBranch.has(item.id)}
            onToggle={toggle}
            onOpen={openPerson}
            isMe={item.id === me.data?.id}
          />
        )}
      />
    </View>
  );
}

function Badge({ label, bg, fg }: { label: string; bg: string; fg: string }) {
  return (
    <Text
      style={[
        text.caption,
        {
          backgroundColor: bg,
          color: fg,
          fontWeight: '600',
          paddingHorizontal: 8,
          borderRadius: 999,
          overflow: 'hidden',
        },
      ]}
    >
      {label}
    </Text>
  );
}

function Legend() {
  const item = (color: string, label: string) => (
    <View key={label} style={{ flexDirection: 'row', alignItems: 'center', gap: 6 }}>
      <View style={{ width: 10, height: 10, borderRadius: 5, backgroundColor: color }} />
      <Text style={[text.caption, { color: colors.textSecondary }]}>{label}</Text>
    </View>
  );
  return (
    <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 16 }}>
      {item(genderColor('MALE'), 'Nam')}
      {item(genderColor('FEMALE'), 'Nữ')}
      {item(colors.accentGreen, 'Đường nối cha mẹ – con')}
      {item(colors.textSecondary, 'Đã mất (nền xám)')}
      {item(colors.border, 'Đã ly hôn (mờ, ngoài tộc)')}
      {item(colors.secondary, 'Trưởng họ')}
      {item(colors.primary, 'Trưởng chi')}
    </View>
  );
}

function TreeRowItem({
  row,
  graph,
  expanded,
  loading,
  onToggle,
  onOpen,
  isMe,
}: {
  isMe: boolean;
  row: TreeRow;
  graph: Graph;
  expanded: boolean;
  loading: boolean;
  onToggle: (id: string) => void;
  onOpen: (id: string) => void;
}) {
  const node = graph.nodes.get(row.id);
  if (!node) return null;
  const spouses = spouseLabels(graph, row.id);
  const meta = [
    lifeSpan(node.birthYear, node.isDeceased, node.deathYear ? String(node.deathYear) : null),
    node.generation ? `Đời ${node.generation}` : '',
  ]
    .filter(Boolean)
    .join(' · ');
  const indent = Math.min(row.level, MAX_INDENT_LEVEL) * INDENT;
  // BR-GEN-28: vợ/chồng ngoài tộc đã ly hôn mờ đi; với thành viên thường thì khóa, không mở được.
  const faded = Boolean(node.divorced);
  return (
    <View style={{ flexDirection: 'row', marginTop: 8 }}>
      {row.level > 0 ? (
        <View
          style={{
            width: indent,
            borderRightWidth: 2,
            borderColor: colors.accentGreen,
            marginRight: 8,
          }}
        />
      ) : null}
      <View
        style={{
          flex: 1,
          opacity: faded ? 0.55 : 1,
          flexDirection: 'row',
          alignItems: 'center',
          backgroundColor: isMe
            ? colors.infoSoft
            : node.isDeceased
              ? colors.surfaceMuted
              : colors.surface,
          borderWidth: isMe ? 2 : 1,
          borderColor: isMe ? colors.info : colors.border,
          // SCRUM-136: vạch trái dày theo màu giới tính, nét đứt không hỗ trợ từng cạnh nên đã mất dùng nền xám + nhãn
          borderLeftWidth: 5,
          borderLeftColor: genderColor(node.gender),
          borderRadius: radius.card,
          paddingVertical: 8,
          paddingLeft: 12,
        }}
      >
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={
            node.locked ? `${node.fullName}, đã ly hôn` : `Xem chi tiết ${node.fullName}`
          }
          accessibilityState={{ disabled: Boolean(node.locked) }}
          disabled={Boolean(node.locked)}
          onPress={() => onOpen(row.id)}
          style={{ flex: 1, flexDirection: 'row', alignItems: 'center', gap: 12, minHeight: 44 }}
        >
          <PersonAvatar name={node.fullName} gender={node.gender} mediaId={node.avatarMediaId} />
          <View style={{ flex: 1 }}>
            <Text style={[text.label, { color: colors.textPrimary }]}>
              {node.fullName}
              {isMe ? <Text style={{ color: colors.info }}> · Bạn</Text> : null}
            </Text>
            {node.isDeceased || node.leaderRole || node.divorced ? (
              <View style={{ flexDirection: 'row', gap: 6, marginTop: 2 }}>
                {node.isDeceased ? (
                  <Badge label="Đã mất" bg={colors.textSecondary} fg={colors.surface} />
                ) : null}
                {node.divorced ? (
                  <Badge label="Đã ly hôn" bg={colors.surfaceMuted} fg={colors.textPrimary} />
                ) : null}
                {node.leaderRole === 'FAMILY_HEAD' ? (
                  <Badge label="Trưởng họ" bg={colors.secondary} fg={colors.primary} />
                ) : null}
                {node.leaderRole === 'BRANCH_HEAD' ? (
                  <Badge label="Trưởng chi" bg={colors.primary} fg={colors.creamOnDark} />
                ) : null}
              </View>
            ) : null}
            {meta ? (
              <Text style={[text.caption, { color: colors.textSecondary }]}>{meta}</Text>
            ) : null}
            {spouses.map((s) => {
              const spouse = graph.nodes.get(s.id);
              return (
                <Text
                  key={s.id}
                  onPress={spouse?.locked ? undefined : () => onOpen(s.id)}
                  accessibilityRole={spouse?.locked ? 'text' : 'button'}
                  style={[
                    text.caption,
                    { color: spouse?.divorced ? colors.textSecondary : colors.textPrimary },
                  ]}
                >
                  Vợ/chồng: {s.label}
                </Text>
              );
            })}
          </View>
        </Pressable>
        {row.expandable ? (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`${expanded ? 'Thu' : 'Mở'} nhánh của ${node.fullName}`}
            accessibilityState={{ expanded }}
            onPress={() => onToggle(row.id)}
            style={{ width: 44, height: 44, alignItems: 'center', justifyContent: 'center' }}
          >
            {loading ? (
              <ActivityIndicator color={colors.primary} />
            ) : (
              <Text style={{ fontSize: 18, color: colors.textSecondary }}>
                {expanded ? '▾' : '▸'}
              </Text>
            )}
          </Pressable>
        ) : (
          <View style={{ width: 12 }} />
        )}
      </View>
    </View>
  );
}
