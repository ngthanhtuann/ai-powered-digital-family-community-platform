// Danh bạ thành viên (UC-DIR-01, FR-DIR-01..04; SCRUM-92): tìm theo họ tên, lọc nghề, nơi ở, thế hệ.
import type { DrawerNavigationProp } from '@react-navigation/drawer';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useInfiniteQuery, useMutation } from '@tanstack/react-query';
import { AdminFamilyPicker } from '../components/AdminFamilyPicker';
import { ErrorText } from '../components/form-ui';
import { dashboardApi } from '../services/admin';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import { EmptyPanel, ErrorPanel, ListLoadingMore, nextPage } from '../components/community-ui';
import { Button, Card, Loading, TextField } from '../components/ui';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';
import { heritageApi, type DirectoryFilter, type DirectoryMember } from '../services/heritage';
import { useFamilyScope } from '../session/FamilyScope';
import { colors, spacing, text } from '../theme';

const EMPTY: DirectoryFilter = { keyword: '', job: '', location: '', generation: '' };

export function DirectoryScreen() {
  const drawer = useNavigation<DrawerNavigationProp<AppDrawerParamList>>();
  const scope = useFamilyScope();
  const familyId = scope.familyId;
  // FR-DSH-05: trưởng chi, trưởng họ, quản trị xuất CSV thành viên ngay ở danh bạ (như web).
  const csv = useMutation({
    mutationFn: () =>
      dashboardApi.exportMembersCsv(scope.familyParam ? { familyId: scope.familyParam } : {}),
  });
  const [draft, setDraft] = useState<DirectoryFilter>(EMPTY);
  // Chỉ gọi API khi bấm Tìm, không gọi mỗi lần gõ phím.
  const [filter, setFilter] = useState<DirectoryFilter>(EMPTY);
  const [showFilters, setShowFilters] = useState(false);
  const query = useInfiniteQuery({
    queryKey: ['directory', filter, familyId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => heritageApi.members(filter, pageParam, familyId),
    getNextPageParam: nextPage<DirectoryMember>,
  });
  const members = useMemo(
    () => query.data?.pages.flatMap((page) => page.items) ?? [],
    [query.data],
  );
  const total = query.data?.pages[0]?.meta.total;
  const set = (key: keyof DirectoryFilter) => (value: string) =>
    setDraft((current) => ({ ...current, [key]: value }));
  const open = (personId: string) =>
    drawer
      .getParent<NativeStackNavigationProp<RootStackParamList>>()
      ?.navigate('PersonDetail', { personId });

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <FlatList
        data={members}
        keyExtractor={(m) => m.id}
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ padding: spacing.page, gap: 12, flexGrow: 1 }}
        onRefresh={() => query.refetch()}
        refreshing={query.isRefetching && !query.isFetchingNextPage}
        onEndReachedThreshold={0.4}
        onEndReached={() => {
          if (query.hasNextPage && !query.isFetchingNextPage && !query.isFetchNextPageError)
            query.fetchNextPage();
        }}
        ListHeaderComponent={
          <View style={{ gap: 12, paddingBottom: 4 }}>
            {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
            {scope.isAdmin && familyId ? (
              <Button
                title="Xuất CSV thành viên"
                variant="secondary"
                loading={csv.isPending}
                onPress={() => csv.mutate()}
              />
            ) : null}
            <ErrorText error={csv.error} />
            <TextField
              label="Tìm theo họ tên"
              value={draft.keyword}
              onChangeText={set('keyword')}
              placeholder="Ví dụ: nguyen van"
              autoCorrect={false}
              returnKeyType="search"
              onSubmitEditing={() => setFilter(draft)}
            />
            {showFilters ? (
              <View style={{ gap: 12 }}>
                <TextField label="Nghề nghiệp" value={draft.job} onChangeText={set('job')} />
                <TextField
                  label="Nơi ở (tỉnh/thành)"
                  value={draft.location}
                  onChangeText={set('location')}
                />
                <TextField
                  label="Thế hệ (đời)"
                  value={draft.generation}
                  onChangeText={set('generation')}
                  keyboardType="number-pad"
                />
              </View>
            ) : null}
            <View style={{ flexDirection: 'row', gap: 8 }}>
              <View style={{ flex: 1 }}>
                <Button title="Tìm" onPress={() => setFilter(draft)} />
              </View>
              <View style={{ flex: 1 }}>
                <Button
                  title={showFilters ? 'Ẩn bộ lọc' : 'Bộ lọc'}
                  variant="secondary"
                  onPress={() => setShowFilters((v) => !v)}
                />
              </View>
            </View>
            {total !== undefined ? (
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                {total} thành viên
              </Text>
            ) : null}
          </View>
        }
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Xem hồ sơ ${item.fullName}`}
            onPress={() => open(item.id)}
          >
            <Card>
              <View style={{ gap: 4 }}>
                <Text style={[text.label, { color: colors.textPrimary }]}>{item.fullName}</Text>
                <Text style={[text.caption, { color: colors.textSecondary }]}>
                  {[
                    item.relationship,
                    item.birthYear ? `Sinh ${item.birthYear}` : null,
                    item.generation ? `Đời ${item.generation}` : null,
                  ]
                    .filter(Boolean)
                    .join(' · ') || 'Thành viên'}
                </Text>
                {item.jobTitle || item.location ? (
                  <Text style={[text.body, { color: colors.textPrimary }]}>
                    {[item.jobTitle, item.location].filter(Boolean).join(' · ')}
                  </Text>
                ) : null}
              </View>
            </Card>
          </Pressable>
        )}
        ListEmptyComponent={
          query.isPending ? (
            <Loading />
          ) : query.isError ? (
            <ErrorPanel error={query.error} retry={() => query.refetch()} />
          ) : (
            <EmptyPanel message="Không có thành viên phù hợp." />
          )
        }
        ListFooterComponent={
          query.isFetchNextPageError ? (
            <ErrorPanel error={query.error} retry={() => query.fetchNextPage()} />
          ) : (
            <ListLoadingMore loading={query.isFetchingNextPage} />
          )
        }
      />
    </View>
  );
}
