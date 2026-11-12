import type { DrawerNavigationProp } from '@react-navigation/drawer';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useInfiniteQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { FlatList, Pressable, Text, View } from 'react-native';
import {
  EmptyPanel,
  ErrorPanel,
  formatWhen,
  ListLoadingMore,
  MediaGallery,
  nextPage,
  useFamilyBranches,
} from '../components/community-ui';
import { Button, Card, Loading } from '../components/ui';
import { communityApi, scopeLabel, type Post } from '../services/community';
import { useSession } from '../session/SessionContext';
import { colors, spacing, text } from '../theme';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';

type FeedFilter = 'ALL' | 'NEWS';

export function FeedScreen() {
  const drawer = useNavigation<DrawerNavigationProp<AppDrawerParamList>>();
  const { session } = useSession();
  const [filter, setFilter] = useState<FeedFilter>('ALL');
  const { names: branchNames } = useFamilyBranches(); // nhãn phạm vi bài (SCRUM-101)
  const familyId =
    session.status === 'signedIn' ? (session.membership.familyId ?? undefined) : undefined;
  const query = useInfiniteQuery({
    queryKey: ['posts', filter, familyId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) =>
      communityApi.posts(pageParam, filter === 'NEWS' ? 'NEWS' : undefined, familyId),
    getNextPageParam: nextPage<Post>,
  });
  const posts = useMemo(() => query.data?.pages.flatMap((page) => page.items) ?? [], [query.data]);
  const open = (postId: string) =>
    drawer
      .getParent<NativeStackNavigationProp<RootStackParamList>>()
      ?.navigate('PostDetail', { postId });

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <FlatList
        data={posts}
        keyExtractor={(post) => post.id}
        contentContainerStyle={{ padding: spacing.page, gap: spacing.gap, flexGrow: 1 }}
        onRefresh={() => query.refetch()}
        refreshing={query.isRefetching && !query.isFetchingNextPage}
        onEndReachedThreshold={0.4}
        onEndReached={() => {
          if (query.hasNextPage && !query.isFetchingNextPage && !query.isFetchNextPageError)
            query.fetchNextPage();
        }}
        ListHeaderComponent={
          <View style={{ gap: 12 }}>
            <Button
              title="Tạo bài đăng"
              onPress={() =>
                drawer
                  .getParent<NativeStackNavigationProp<RootStackParamList>>()
                  ?.navigate('CreatePost')
              }
            />
            <View style={{ flexDirection: 'row', gap: 8 }}>
              {(['ALL', 'NEWS'] as const).map((value) => (
                <Pressable
                  key={value}
                  onPress={() => setFilter(value)}
                  accessibilityRole="button"
                  accessibilityState={{ selected: filter === value }}
                  style={{
                    paddingVertical: 9,
                    paddingHorizontal: 16,
                    borderRadius: 6,
                    borderWidth: 1,
                    borderColor: colors.border,
                    backgroundColor: filter === value ? colors.secondary : colors.surface,
                  }}
                >
                  <Text style={[text.label, { color: colors.textPrimary }]}>
                    {value === 'ALL' ? 'Tất cả' : 'Tin tức'}
                  </Text>
                </Pressable>
              ))}
            </View>
          </View>
        }
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Xem bài của ${item.authorName}`}
            onPress={() => open(item.id)}
          >
            <Card>
              <Text style={[text.label, { color: colors.textPrimary }]}>{item.authorName}</Text>
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                {formatWhen(item.createdAt)} · {scopeLabel(item.branchId, branchNames)} ·{' '}
                {item.type === 'NEWS' ? 'Tin tức' : 'Bài đăng'}
              </Text>
              <Text style={[text.body, { color: colors.textPrimary }]} numberOfLines={5}>
                {item.content}
              </Text>
              <MediaGallery media={item.media ?? []} />
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                {Object.values(item.reactionCounts).reduce((sum, n) => sum + n, 0)} cảm xúc ·{' '}
                {item.commentCount} bình luận
                {item.myReaction ? ` · Đã thả ${item.myReaction}` : ''}
              </Text>
              <Text style={[text.label, { color: colors.primary }]}>Xem chi tiết và tương tác</Text>
            </Card>
          </Pressable>
        )}
        ListEmptyComponent={
          query.isPending ? (
            <Loading />
          ) : query.isError ? (
            <ErrorPanel error={query.error} retry={() => query.refetch()} />
          ) : (
            <EmptyPanel message="Chưa có bài đăng nào." />
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
