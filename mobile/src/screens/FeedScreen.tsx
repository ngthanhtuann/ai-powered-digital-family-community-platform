import type { DrawerNavigationProp } from '@react-navigation/drawer';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
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
import { PersonAvatar } from '../components/genealogy-ui';
import { communityApi, scopeLabel, type Post, type ReactionType } from '../services/community';
import { useSession } from '../session/SessionContext';
import { colors, spacing, text } from '../theme';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';

type FeedFilter = 'ALL' | 'NEWS';
const QUICK_REACTIONS: { type: ReactionType; icon: string }[] = [
  { type: 'LIKE', icon: '👍' },
  { type: 'LOVE', icon: '❤️' },
  { type: 'HAHA', icon: '😄' },
  { type: 'SAD', icon: '😢' },
];

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
  const qc = useQueryClient();
  const react = useMutation({
    mutationFn: ({ post, type }: { post: Post; type: ReactionType }) =>
      post.myReaction === type
        ? communityApi.removeReaction(post.id)
        : communityApi.react(post.id, type),
    onSettled: () => qc.invalidateQueries({ queryKey: ['posts'] }),
  });
  const open = (postId: string) =>
    drawer
      .getParent<NativeStackNavigationProp<RootStackParamList>>()
      ?.navigate('PostDetail', { postId });
  const openPerson = (personId: string) =>
    drawer
      .getParent<NativeStackNavigationProp<RootStackParamList>>()
      ?.navigate('PersonDetail', { personId });

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
              {/* Bấm ảnh hoặc tên người đăng để mở trang cá nhân (SCRUM-130). */}
              <Pressable
                accessibilityRole={item.authorPersonId ? 'link' : undefined}
                accessibilityLabel={`Trang cá nhân ${item.authorName ?? ''}`}
                disabled={!item.authorPersonId}
                onPress={() => item.authorPersonId && openPerson(item.authorPersonId)}
                style={{ flexDirection: 'row', alignItems: 'center', gap: 10 }}
              >
                <PersonAvatar name={item.authorName} mediaId={item.authorAvatarMediaId} size={40} />
                <View style={{ flex: 1 }}>
                  <Text
                    style={[
                      text.label,
                      { color: item.authorPersonId ? colors.info : colors.textPrimary },
                    ]}
                  >
                    {item.authorName}
                  </Text>
                  <Text style={[text.caption, { color: colors.textSecondary }]}>
                    {formatWhen(item.createdAt)} ·{' '}
                    {scopeLabel(item.branchId, branchNames, item.audience)} ·{' '}
                    {item.type === 'NEWS' ? 'Tin tức' : 'Bài đăng'}
                  </Text>
                </View>
              </Pressable>
              <Text style={[text.body, { color: colors.textPrimary }]} numberOfLines={8}>
                {item.content}
              </Text>
              <MediaGallery media={item.media ?? []} />
              {/* Thả cảm xúc ngay trên thẻ như web; chạm lại cảm xúc đang chọn để bỏ. */}
              <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 6 }}>
                {QUICK_REACTIONS.map(({ type, icon }) => (
                  <Pressable
                    key={type}
                    accessibilityRole="button"
                    accessibilityState={{ selected: item.myReaction === type }}
                    accessibilityLabel={`Thả ${type}`}
                    disabled={react.isPending}
                    onPress={() => react.mutate({ post: item, type })}
                    style={{
                      minHeight: 40,
                      paddingHorizontal: 12,
                      justifyContent: 'center',
                      borderRadius: 20,
                      borderWidth: 1,
                      borderColor: item.myReaction === type ? colors.primary : colors.border,
                      backgroundColor: item.myReaction === type ? colors.infoSoft : colors.surface,
                    }}
                  >
                    <Text style={[text.label, { color: colors.textPrimary }]}>
                      {icon} {item.reactionCounts[type] ?? 0}
                    </Text>
                  </Pressable>
                ))}
              </View>
              <Text style={[text.label, { color: colors.primary }]}>
                {item.commentCount} bình luận · Xem và bình luận
              </Text>
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
