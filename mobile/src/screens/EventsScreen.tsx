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
  nextPage,
} from '../components/community-ui';
import { Card, Loading } from '../components/ui';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';
import { eventsApi, RSVP_LABEL, type Event } from '../services/events';
import { useSession } from '../session/SessionContext';
import { colors, spacing, text } from '../theme';

type Period = 'upcoming' | 'past';

export function EventsScreen() {
  const drawer = useNavigation<DrawerNavigationProp<AppDrawerParamList>>();
  const { session } = useSession();
  const [period, setPeriod] = useState<Period>('upcoming');
  const [now, setNow] = useState(() => new Date().toISOString());
  const familyId =
    session.status === 'signedIn' ? (session.membership.familyId ?? undefined) : undefined;
  const events = useInfiniteQuery({
    queryKey: ['events', period, now, familyId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => eventsApi.list(pageParam, period, now, familyId),
    getNextPageParam: nextPage<Event>,
  });
  const items = useMemo(() => events.data?.pages.flatMap((p) => p.items) ?? [], [events.data]);
  const choosePeriod = (value: Period) => {
    setNow(new Date().toISOString());
    setPeriod(value);
  };
  const open = (eventId: string) =>
    drawer
      .getParent<NativeStackNavigationProp<RootStackParamList>>()
      ?.navigate('EventDetail', { eventId });

  return (
    <View style={{ flex: 1, backgroundColor: colors.background }}>
      <FlatList
        data={items}
        keyExtractor={(item) => item.id}
        contentContainerStyle={{ padding: spacing.page, gap: spacing.gap, flexGrow: 1 }}
        onRefresh={() => setNow(new Date().toISOString())}
        refreshing={events.isRefetching && !events.isFetchingNextPage}
        onEndReachedThreshold={0.4}
        onEndReached={() => {
          if (events.hasNextPage && !events.isFetchingNextPage && !events.isFetchNextPageError)
            events.fetchNextPage();
        }}
        ListHeaderComponent={
          <View style={{ flexDirection: 'row', gap: 8 }}>
            {(['upcoming', 'past'] as const).map((value) => (
              <Pressable
                key={value}
                onPress={() => choosePeriod(value)}
                accessibilityRole="button"
                accessibilityState={{ selected: period === value }}
                style={{
                  flex: 1,
                  borderColor: colors.border,
                  borderWidth: 1,
                  borderRadius: 6,
                  padding: 12,
                  alignItems: 'center',
                  backgroundColor: period === value ? colors.secondary : colors.surface,
                }}
              >
                <Text style={[text.label, { color: colors.textPrimary }]}>
                  {value === 'upcoming' ? 'Sắp tới' : 'Đã qua'}
                </Text>
              </Pressable>
            ))}
          </View>
        }
        renderItem={({ item }) => (
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={`Xem sự kiện ${item.title}`}
            onPress={() => open(item.id)}
          >
            <Card title={item.title}>
              <Text style={[text.label, { color: colors.primary }]}>
                {formatWhen(item.startAt)}
              </Text>
              {item.location ? (
                <Text style={[text.body, { color: colors.textSecondary }]}>
                  Địa điểm: {item.location}
                </Text>
              ) : null}
              {item.description ? (
                <Text style={[text.body, { color: colors.textPrimary }]} numberOfLines={3}>
                  {item.description}
                </Text>
              ) : null}
              <Text style={[text.caption, { color: colors.textSecondary }]}>
                Tham dự: {item.rsvpCounts.going} · Có thể: {item.rsvpCounts.maybe} · Không:{' '}
                {item.rsvpCounts.notGoing}
              </Text>
              <Text style={[text.label, { color: colors.primary }]}>
                {item.myRsvpStatus
                  ? `Phản hồi của bạn: ${RSVP_LABEL[item.myRsvpStatus]}`
                  : 'Xem chi tiết / RSVP'}
              </Text>
            </Card>
          </Pressable>
        )}
        ListEmptyComponent={
          events.isPending ? (
            <Loading />
          ) : events.isError ? (
            <ErrorPanel error={events.error} retry={() => events.refetch()} />
          ) : (
            <EmptyPanel
              message={
                period === 'upcoming' ? 'Chưa có sự kiện sắp tới.' : 'Chưa có sự kiện đã qua.'
              }
            />
          )
        }
        ListFooterComponent={
          events.isFetchNextPageError ? (
            <ErrorPanel error={events.error} retry={() => events.fetchNextPage()} />
          ) : (
            <ListLoadingMore loading={events.isFetchingNextPage} />
          )
        }
      />
    </View>
  );
}
