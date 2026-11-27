// Di sản (UC-HER-01..04, FR-HER-01..05; SCRUM-92): kho lưu trữ số, câu chuyện gia đình, thư viện ảnh.
// Người quản lý thêm tài liệu, câu chuyện, ảnh như web (BR-HER-01).
import type { DrawerNavigationProp } from '@react-navigation/drawer';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import {
  useInfiniteQuery,
  type UseInfiniteQueryResult,
  type InfiniteData,
} from '@tanstack/react-query';
import { useMemo, useState, type ReactElement, type ReactNode } from 'react';
import { FlatList, Pressable, Text, useWindowDimensions, View } from 'react-native';
import { EmptyPanel, ErrorPanel, ListLoadingMore, nextPage } from '../components/community-ui';
import { AuthImage, formatDate } from '../components/heritage-ui';
import { Card, Loading } from '../components/ui';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';
import type { ApiMeta } from '../services/api-types';
import {
  heritageApi,
  type FamilyStory,
  type HeritageDocument,
  type HeritagePhoto,
} from '../services/heritage';
import { useFamilyScope } from '../session/FamilyScope';
import { HeritageActions } from './ContentManageScreens';
import { colors, spacing, text } from '../theme';

type Tab = 'documents' | 'stories' | 'photos';
const TABS: { value: Tab; label: string }[] = [
  { value: 'documents', label: 'Tài liệu' },
  { value: 'stories', label: 'Câu chuyện' },
  { value: 'photos', label: 'Thư viện ảnh' },
];
const COLUMNS = 3;
const GAP = 6;

type Page<T> = { items: T[]; meta: ApiMeta };

/** Danh sách cuộn vô hạn dùng chung cho ba tab: trạng thái tải, rỗng, lỗi, tải thêm, kéo để làm mới. */
function HeritageList<T>({
  query,
  header,
  renderItem,
  keyOf,
  empty,
  columns = 1,
}: {
  query: UseInfiniteQueryResult<InfiniteData<Page<T>, unknown>>;
  header: ReactNode;
  renderItem: (item: T) => ReactElement;
  keyOf: (item: T) => string;
  empty: string;
  columns?: number;
}) {
  const items = useMemo(() => query.data?.pages.flatMap((page) => page.items) ?? [], [query.data]);
  return (
    <FlatList
      // Đổi số cột phải tạo lại FlatList (giới hạn của React Native).
      key={`cols-${columns}`}
      data={items}
      numColumns={columns}
      columnWrapperStyle={columns > 1 ? { gap: GAP } : undefined}
      keyExtractor={keyOf}
      contentContainerStyle={{ padding: spacing.page, gap: columns > 1 ? GAP : 12, flexGrow: 1 }}
      // Không có thumbnail ở backend: chỉ render số ảnh vừa màn hình, phần còn lại tải khi cuộn tới.
      initialNumToRender={columns > 1 ? 12 : 8}
      windowSize={5}
      onRefresh={() => query.refetch()}
      refreshing={query.isRefetching && !query.isFetchingNextPage}
      onEndReachedThreshold={0.4}
      onEndReached={() => {
        if (query.hasNextPage && !query.isFetchingNextPage && !query.isFetchNextPageError)
          query.fetchNextPage();
      }}
      ListHeaderComponent={<>{header}</>}
      renderItem={({ item }) => renderItem(item)}
      ListEmptyComponent={
        query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <ErrorPanel error={query.error} retry={() => query.refetch()} />
        ) : (
          <EmptyPanel message={empty} />
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
  );
}

export function HeritageScreen() {
  const drawer = useNavigation<DrawerNavigationProp<AppDrawerParamList>>();
  const stack = () => drawer.getParent<NativeStackNavigationProp<RootStackParamList>>();
  const scope = useFamilyScope();
  const familyId = scope.familyId;
  const [tab, setTab] = useState<Tab>('documents');
  const { width } = useWindowDimensions();
  const tile = Math.floor((width - spacing.page * 2 - GAP * (COLUMNS - 1)) / COLUMNS);

  // Chỉ tab đang mở mới gọi API.
  const documents = useInfiniteQuery({
    queryKey: ['heritage', 'documents', familyId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => heritageApi.documents(pageParam, familyId),
    getNextPageParam: nextPage<HeritageDocument>,
    enabled: tab === 'documents',
  });
  const stories = useInfiniteQuery({
    queryKey: ['heritage', 'stories', familyId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => heritageApi.stories(pageParam, familyId),
    getNextPageParam: nextPage<FamilyStory>,
    enabled: tab === 'stories',
  });
  const photos = useInfiniteQuery({
    queryKey: ['heritage', 'photos', familyId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => heritageApi.photos(pageParam, familyId),
    getNextPageParam: nextPage<HeritagePhoto>,
    enabled: tab === 'photos',
  });

  const header = (
    <View style={{ gap: 12, paddingBottom: 4 }}>
      <HeritageActions stack={stack()} />
      <View style={{ flexDirection: 'row', gap: 8, flexWrap: 'wrap' }}>
        {TABS.map(({ value, label }) => (
          <Pressable
            key={value}
            onPress={() => setTab(value)}
            accessibilityRole="tab"
            accessibilityState={{ selected: tab === value }}
            style={{
              minHeight: 44,
              justifyContent: 'center',
              paddingHorizontal: 14,
              borderRadius: 6,
              borderWidth: 1,
              borderColor: colors.border,
              backgroundColor: tab === value ? colors.secondary : colors.surface,
            }}
          >
            <Text style={[text.label, { color: colors.textPrimary }]}>{label}</Text>
          </Pressable>
        ))}
      </View>
    </View>
  );

  if (tab === 'photos') {
    return (
      <View style={{ flex: 1, backgroundColor: colors.background }}>
        <HeritageList
          query={photos}
          header={header}
          columns={COLUMNS}
          keyOf={(p) => p.mediaId}
          empty="Chưa có ảnh nào."
          renderItem={(p) => {
            const caption = p.caption || 'Ảnh dòng họ';
            return (
              <Pressable
                accessibilityRole="imagebutton"
                accessibilityLabel={`Phóng to ${caption}`}
                onPress={() =>
                  stack()?.navigate('PhotoViewer', { mediaId: p.mediaId, title: caption })
                }
              >
                <AuthImage
                  mediaId={p.mediaId}
                  label={caption}
                  style={{ width: tile, height: tile, borderRadius: 6 }}
                />
              </Pressable>
            );
          }}
        />
      </View>
    );
  }

  if (tab === 'stories') {
    return (
      <View style={{ flex: 1, backgroundColor: colors.background }}>
        <HeritageList
          query={stories}
          header={header}
          keyOf={(s) => s.id}
          empty="Chưa có câu chuyện nào."
          renderItem={(s) => (
            <Pressable
              accessibilityRole="button"
              accessibilityLabel={`Đọc câu chuyện ${s.title}`}
              onPress={() => stack()?.navigate('StoryDetail', { storyId: s.id })}
            >
              <Card>
                <View style={{ gap: 4 }}>
                  <Text style={[text.label, { color: colors.textPrimary }]}>{s.title}</Text>
                  <Text style={[text.caption, { color: colors.textSecondary }]}>
                    {s.authorName} · {formatDate(s.createdAt)}
                  </Text>
                  <Text style={[text.body, { color: colors.textPrimary }]} numberOfLines={3}>
                    {s.content}
                  </Text>
                </View>
              </Card>
            </Pressable>
          )}
        />
      </View>
    );
  }

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <HeritageList
        query={documents}
        header={header}
        keyOf={(d) => d.id}
        empty="Chưa có tài liệu nào."
        renderItem={(d) => (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Xem tài liệu ${d.title}`}
            onPress={() =>
              stack()?.navigate('PhotoViewer', {
                mediaId: d.mediaId,
                title: d.title,
                document: true,
                documentId: d.id,
              })
            }
          >
            <Card>
              <View style={{ gap: 4 }}>
                <Text style={[text.label, { color: colors.textPrimary }]}>{d.title}</Text>
                <Text style={[text.caption, { color: colors.textSecondary }]}>
                  {[d.category, formatDate(d.createdAt)].filter(Boolean).join(' · ')}
                </Text>
                {d.description ? (
                  <Text style={[text.body, { color: colors.textPrimary }]} numberOfLines={3}>
                    {d.description}
                  </Text>
                ) : null}
              </View>
            </Card>
          </Pressable>
        )}
      />
    </View>
  );
}
