import { useNavigation, type NavigationProp } from '@react-navigation/native';
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { FlatList, Text, View } from 'react-native';
import {
  EmptyPanel,
  ErrorPanel,
  formatWhen,
  ListLoadingMore,
  nextPage,
} from '../components/community-ui';
import { Button, Card, Loading, Notice } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { notificationsApi, type Notification } from '../services/notifications';
import { colors, spacing, text } from '../theme';

export function NotificationsScreen() {
  const navigation = useNavigation<NavigationProp<RootStackParamList>>();
  const qc = useQueryClient();
  const [markingId, setMarkingId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const query = useInfiniteQuery({
    queryKey: ['notifications', 'list'],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => notificationsApi.list(pageParam),
    getNextPageParam: nextPage<Notification>,
  });
  const items = useMemo(() => query.data?.pages.flatMap((p) => p.items) ?? [], [query.data]);

  const markRead = async (item: Notification) => {
    setMarkingId(item.id);
    setError(null);
    try {
      if (!item.isRead) await notificationsApi.markRead(item.id);
      await Promise.all([
        qc.invalidateQueries({ queryKey: ['notifications', 'unread'] }),
        qc.invalidateQueries({ queryKey: ['notifications', 'list'] }),
      ]);
    } catch (caught) {
      setError((caught as Error).message);
    } finally {
      setMarkingId(null);
    }
  };

  const openRelated = (item: Notification) => {
    if (!item.refId) return;
    if (item.refType === 'EVENT') navigation.navigate('EventDetail', { eventId: item.refId });
    if (item.refType === 'POST') navigation.navigate('PostDetail', { postId: item.refId });
  };

  return (
    <FlatList
      style={{ backgroundColor: colors.background }}
      data={items}
      keyExtractor={(item) => item.id}
      contentContainerStyle={{ padding: spacing.page, gap: spacing.gap, flexGrow: 1 }}
      onRefresh={() => query.refetch()}
      refreshing={query.isRefetching && !query.isFetchingNextPage}
      onEndReachedThreshold={0.4}
      onEndReached={() => {
        if (query.hasNextPage && !query.isFetchingNextPage && !query.isFetchNextPageError)
          query.fetchNextPage();
      }}
      ListHeaderComponent={
        error ? (
          <Notice variant="error" title="Không cập nhật được thông báo" message={error} />
        ) : undefined
      }
      renderItem={({ item }) => (
        <Card>
          <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }}>
            {!item.isRead ? (
              <View
                style={{ width: 8, height: 8, borderRadius: 4, backgroundColor: colors.error }}
              />
            ) : null}
            <Text style={[text.sectionTitle, { flex: 1, color: colors.textPrimary }]}>
              {item.title}
            </Text>
          </View>
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            {formatWhen(item.createdAt)}
          </Text>
          <Text style={[text.body, { color: colors.textPrimary }]}>{item.content}</Text>
          {!item.isRead ? (
            <Button
              title="Đánh dấu đã đọc"
              variant="secondary"
              loading={markingId === item.id}
              disabled={markingId !== null && markingId !== item.id}
              onPress={() => markRead(item)}
            />
          ) : (
            <Text style={[text.caption, { color: colors.success }]}>Đã đọc</Text>
          )}
          {item.refId && (item.refType === 'EVENT' || item.refType === 'POST') ? (
            <Button
              title="Xem nội dung liên quan"
              variant="secondary"
              onPress={() => openRelated(item)}
            />
          ) : null}
        </Card>
      )}
      ListEmptyComponent={
        query.isPending ? (
          <Loading />
        ) : query.isError ? (
          <ErrorPanel error={query.error} retry={() => query.refetch()} />
        ) : (
          <EmptyPanel message="Bạn chưa có thông báo nào." />
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
