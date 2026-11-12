import { useRoute, type RouteProp } from '@react-navigation/native';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { Text, View } from 'react-native';
import { ErrorPanel, formatWhen, MediaGallery, nextPage } from '../components/community-ui';
import { Button, Card, Loading, Notice, Screen } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import {
  eventsApi,
  RSVP_LABEL,
  type EventMedia,
  type EventRsvp,
  type Participant,
} from '../services/events';
import { colors, text } from '../theme';

const RSVP: { value: EventRsvp; title: string }[] = [
  { value: 'GOING', title: 'Tham dự' },
  { value: 'NOT_GOING', title: 'Không' },
  { value: 'MAYBE', title: 'Có thể' },
];

export function EventDetailScreen() {
  const { eventId } = useRoute<RouteProp<RootStackParamList, 'EventDetail'>>().params;
  const qc = useQueryClient();
  const [notice, setNotice] = useState<string | null>(null);
  const [referenceNow, setReferenceNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setReferenceNow(Date.now()), 30000);
    return () => clearInterval(timer);
  }, []);
  const event = useQuery({
    queryKey: ['event', eventId],
    queryFn: () => eventsApi.detail(eventId),
  });
  const participants = useInfiniteQuery({
    queryKey: ['participants', eventId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => eventsApi.participants(eventId, pageParam),
    getNextPageParam: nextPage<Participant>,
  });
  const photos = useInfiniteQuery({
    queryKey: ['event-media', eventId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => eventsApi.media(eventId, pageParam),
    getNextPageParam: nextPage<EventMedia>,
  });
  const people = useMemo(
    () => participants.data?.pages.flatMap((p) => p.items) ?? [],
    [participants.data],
  );
  const gallery = useMemo(() => photos.data?.pages.flatMap((p) => p.items) ?? [], [photos.data]);
  const rsvp = useMutation({
    mutationFn: (value: EventRsvp) => eventsApi.rsvp(eventId, value),
    onSuccess: () => {
      setNotice(null);
      qc.invalidateQueries({ queryKey: ['event', eventId] });
      qc.invalidateQueries({ queryKey: ['events'] });
      qc.invalidateQueries({ queryKey: ['participants', eventId] });
    },
    onError: (error) => setNotice(error.message),
  });

  return (
    <Screen>
      {event.isPending ? (
        <Loading />
      ) : event.isError ? (
        <ErrorPanel error={event.error} retry={() => event.refetch()} />
      ) : (
        <Card title={event.data.title}>
          <Text style={[text.label, { color: colors.primary }]}>
            Bắt đầu: {formatWhen(event.data.startAt)}
          </Text>
          {event.data.endAt ? (
            <Text style={[text.body, { color: colors.textSecondary }]}>
              Kết thúc: {formatWhen(event.data.endAt)}
            </Text>
          ) : null}
          {event.data.location ? (
            <Text style={[text.body, { color: colors.textSecondary }]}>
              Địa điểm: {event.data.location}
            </Text>
          ) : null}
          {event.data.description ? (
            <Text style={[text.body, { color: colors.textPrimary }]}>{event.data.description}</Text>
          ) : null}
          <Text style={[text.label, { color: colors.textPrimary }]}>
            Phản hồi của bạn:{' '}
            {event.data.myRsvpStatus ? RSVP_LABEL[event.data.myRsvpStatus] : 'Chưa chọn'}
          </Text>
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            Tham dự: {event.data.rsvpCounts.going} · Không: {event.data.rsvpCounts.notGoing} · Có
            thể: {event.data.rsvpCounts.maybe}
          </Text>
          {new Date(event.data.startAt).getTime() > referenceNow ? (
            <View style={{ gap: 8 }}>
              {RSVP.map(({ value, title }) => (
                <Button
                  key={value}
                  title={`${event.data.myRsvpStatus === value ? '✓ ' : ''}${title}`}
                  variant="secondary"
                  disabled={rsvp.isPending}
                  onPress={() => rsvp.mutate(value)}
                />
              ))}
            </View>
          ) : (
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              Sự kiện đã bắt đầu, không thể đổi RSVP.
            </Text>
          )}
          {notice ? (
            <Notice variant="error" title="Không cập nhật được RSVP" message={notice} />
          ) : null}
        </Card>
      )}

      <Card title="Người tham dự">
        {participants.isPending ? (
          <Loading />
        ) : participants.isError ? (
          <ErrorPanel error={participants.error} retry={() => participants.refetch()} />
        ) : people.length === 0 ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>Chưa có phản hồi RSVP.</Text>
        ) : (
          people.map((p) => (
            <View
              key={p.id}
              style={{ borderBottomWidth: 1, borderBottomColor: colors.border, paddingBottom: 8 }}
            >
              <Text style={[text.body, { color: colors.textPrimary }]}>
                {p.displayName ?? 'Thành viên'} — {RSVP_LABEL[p.rsvpStatus]}
              </Text>
            </View>
          ))
        )}
        {participants.hasNextPage ? (
          <Button
            title="Tải thêm người tham dự"
            variant="secondary"
            loading={participants.isFetchingNextPage}
            onPress={() => participants.fetchNextPage()}
          />
        ) : null}
      </Card>

      <Card title="Thư viện ảnh sự kiện">
        {photos.isPending ? (
          <Loading />
        ) : photos.isError ? (
          <ErrorPanel error={photos.error} retry={() => photos.refetch()} />
        ) : gallery.length === 0 ? (
          <Text style={[text.body, { color: colors.textSecondary }]}>Chưa có ảnh sự kiện.</Text>
        ) : (
          <MediaGallery media={gallery} />
        )}
        {photos.hasNextPage ? (
          <Button
            title="Tải thêm ảnh"
            variant="secondary"
            loading={photos.isFetchingNextPage}
            onPress={() => photos.fetchNextPage()}
          />
        ) : null}
      </Card>
    </Screen>
  );
}
