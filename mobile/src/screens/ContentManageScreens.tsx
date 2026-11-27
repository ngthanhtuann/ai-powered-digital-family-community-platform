// Thêm, sửa, xóa nội dung trên mobile như web: thông báo gia đình (components/community/Announcements), sự kiện
// (app/events: tạo, sửa, hủy, điểm danh, ảnh), di sản (tài liệu, câu chuyện, ảnh, người tiêu biểu). Quyền do backend kiểm.
import type { DrawerScreenProps } from '@react-navigation/drawer';
import type {
  NativeStackNavigationProp,
  NativeStackScreenProps,
} from '@react-navigation/native-stack';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useEffect, useState } from 'react';
import { Text, View } from 'react-native';
import { AdminFamilyPicker } from '../components/AdminFamilyPicker';
import { EmptyPanel, formatWhen, nextPage, toUploadAsset } from '../components/community-ui';
import {
  Checkbox,
  ErrorText,
  Pager,
  Pill,
  Select,
  TextArea,
  confirmAction,
} from '../components/form-ui';
import { Button, Card, Loading, Notice, Screen, TextField } from '../components/ui';
import type { AppDrawerParamList, RootStackParamList } from '../navigation/types';
import { announcementsApi, communityApi, type Announcement } from '../services/community';
import { eventManageApi, eventsApi, type Event, type Participant } from '../services/events';
import { genealogyManageApi } from '../services/genealogy';
import { heritageApi, heritageManageApi, type OutstandingMember } from '../services/heritage';
import { useFamilyScope } from '../session/FamilyScope';
import { colors, text } from '../theme';

type Stack = NativeStackNavigationProp<RootStackParamList>;
type StackProps<K extends keyof RootStackParamList> = NativeStackScreenProps<RootStackParamList, K>;

/** Chọn một ảnh (jpg, png, webp; server giới hạn 5 MB) từ thư viện của máy. */
async function pickImage() {
  const result = await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ['images'],
    quality: 0.8,
  });
  if (result.canceled || !result.assets[0]) return null;
  return toUploadAsset(result.assets[0]);
}

// ---------------------------------------------------------------- Sự kiện

// Ô thời gian nhập theo giờ dòng họ (Asia/Ho_Chi_Minh, UTC+7), khớp giờ hiển thị và giờ backend dùng để nhắc.
const VN_OFFSET_MS = 7 * 60 * 60 * 1000;
const DATETIME_PATTERN = /^\d{4}-\d{2}-\d{2} ([01]\d|2[0-3]):[0-5]\d$/;
export function toVnInput(iso?: string | null) {
  if (!iso) return '';
  const d = new Date(new Date(iso).getTime() + VN_OFFSET_MS);
  return d.toISOString().slice(0, 16).replace('T', ' ');
}
export function fromVnInput(value: string): string | null {
  if (!DATETIME_PATTERN.test(value)) return null;
  const d = new Date(`${value.replace(' ', 'T')}:00+07:00`);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
}

/** UC-EVT-01: tạo hoặc sửa sự kiện (cùng giới hạn với backend; backend vẫn kiểm tra lại). */
export function EventFormScreen({ navigation, route }: StackProps<'EventForm'>) {
  const eventId = route.params.eventId;
  const existing = useQuery({
    queryKey: ['event', eventId],
    queryFn: () => eventsApi.detail(eventId!),
    enabled: Boolean(eventId),
  });
  useEffect(() => {
    navigation.setOptions({ title: eventId ? 'Sửa sự kiện' : 'Tạo sự kiện' });
  }, [navigation, eventId]);
  if (eventId && existing.isPending) return <Loading />;
  return <EventForm event={existing.data} onDone={() => navigation.goBack()} />;
}

function EventForm({ event, onDone }: { event?: Event; onDone: () => void }) {
  const cache = useQueryClient();
  const scope = useFamilyScope();
  const [title, setTitle] = useState(event?.title ?? '');
  const [start, setStart] = useState(toVnInput(event?.startAt));
  const [end, setEnd] = useState(toVnInput(event?.endAt));
  const [location, setLocation] = useState(event?.location ?? '');
  const [description, setDescription] = useState(event?.description ?? '');
  const [touched, setTouched] = useState(false);
  const startIso = fromVnInput(start);
  const endIso = end ? fromVnInput(end) : null;
  const errors = {
    title: !title.trim() ? 'Tên sự kiện không được để trống' : undefined,
    start: !startIso ? 'Thời gian bắt đầu theo dạng YYYY-MM-DD HH:mm' : undefined,
    end:
      end && !endIso
        ? 'Thời gian kết thúc theo dạng YYYY-MM-DD HH:mm'
        : endIso && startIso && new Date(endIso) <= new Date(startIso)
          ? 'Kết thúc phải sau thời điểm bắt đầu'
          : undefined,
  };
  const invalid = Object.values(errors).some(Boolean);
  const save = useMutation({
    mutationFn: () =>
      event
        ? eventManageApi.update(event.id, {
            title: title.trim(),
            description,
            startAt: startIso!,
            endAt: endIso,
            location,
          })
        : eventManageApi.create(
            {
              title: title.trim(),
              startAt: startIso!,
              ...(description ? { description } : {}),
              ...(endIso ? { endAt: endIso } : {}),
              ...(location ? { location } : {}),
            },
            scope.familyParam,
          ),
    onSuccess: async () => {
      await Promise.all(
        ['events', 'event'].map((key) => cache.invalidateQueries({ queryKey: [key] })),
      );
      onDone();
    },
  });
  const show = (msg?: string) => (touched ? msg : undefined);
  return (
    <Screen>
      <Card>
        <TextField
          label="Tên sự kiện"
          maxLength={255}
          value={title}
          onChangeText={setTitle}
          error={show(errors.title)}
        />
        <TextField
          label="Bắt đầu (YYYY-MM-DD HH:mm, giờ Việt Nam)"
          placeholder="2026-12-24 18:00"
          keyboardType="numbers-and-punctuation"
          maxLength={16}
          value={start}
          onChangeText={setStart}
          error={show(errors.start)}
        />
        <TextField
          label="Kết thúc (không bắt buộc)"
          placeholder="2026-12-24 21:00"
          keyboardType="numbers-and-punctuation"
          maxLength={16}
          value={end}
          onChangeText={setEnd}
          error={show(errors.end)}
        />
        <TextField label="Địa điểm" maxLength={255} value={location} onChangeText={setLocation} />
        <TextArea
          label="Mô tả"
          maxLength={5000}
          value={description}
          onChangeText={setDescription}
        />
        <ErrorText error={save.error} />
        <Button
          title={event ? 'Lưu' : 'Tạo sự kiện'}
          loading={save.isPending}
          onPress={() => {
            setTouched(true);
            if (!invalid) save.mutate();
          }}
        />
      </Card>
    </Screen>
  );
}

/** Sửa, hủy sự kiện: người tạo hoặc người quản lý (như canManage của web). */
export function EventManagePanel({
  event,
  stack,
  onDeleted,
}: {
  event: Event;
  stack: Stack;
  onDeleted: () => void;
}) {
  const cache = useQueryClient();
  const scope = useFamilyScope();
  const remove = useMutation({
    mutationFn: () => eventManageApi.remove(event.id),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['events'] });
      onDeleted();
    },
  });
  if (event.createdBy !== scope.userId && !scope.isAdmin) return null;
  return (
    <View style={{ gap: 8 }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button
          title="Sửa"
          variant="secondary"
          onPress={() => stack.push('EventForm', { eventId: event.id })}
        />
        <Button
          title="Hủy sự kiện"
          variant="danger"
          loading={remove.isPending}
          onPress={() =>
            confirmAction('Hủy sự kiện', 'Hủy sự kiện này?', () => remove.mutate(), 'Hủy sự kiện')
          }
        />
      </View>
      <ErrorText error={remove.error} />
    </View>
  );
}

/** UC-EVT-03: người quản lý điểm danh người tham dự. */
export function AttendanceToggle({
  eventId,
  participant,
}: {
  eventId: string;
  participant: Participant;
}) {
  const cache = useQueryClient();
  const scope = useFamilyScope();
  const toggle = useMutation({
    mutationFn: (attended: boolean) =>
      eventManageApi.updateAttendance(eventId, participant.id, attended),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['participants', eventId] }),
  });
  if (!scope.isAdmin) return null;
  return (
    <View>
      <Checkbox
        label="Đã điểm danh"
        checked={Boolean(participant.attendedAt)}
        onChange={(attended) => toggle.mutate(attended)}
      />
      <ErrorText error={toggle.error} />
    </View>
  );
}

/** UC-EVT-04: thành viên thêm ảnh vào thư viện ảnh sự kiện. */
export function EventPhotoUpload({ eventId }: { eventId: string }) {
  const cache = useQueryClient();
  const upload = useMutation({
    mutationFn: async () => {
      const file = await pickImage();
      if (file) await eventManageApi.uploadMedia(eventId, file);
    },
    onSuccess: () => cache.invalidateQueries({ queryKey: ['event-media', eventId] }),
  });
  return (
    <View style={{ gap: 8 }}>
      <Button
        title="Thêm ảnh sự kiện"
        variant="secondary"
        loading={upload.isPending}
        onPress={() => upload.mutate()}
      />
      <ErrorText error={upload.error} />
    </View>
  );
}

// ---------------------------------------------------------------- Thông báo gia đình

/** UC-COM-06: thông báo chung; trưởng chi, trưởng họ, quản trị đăng, sửa, ghim, xóa; thành viên báo cáo vi phạm. */
export function AnnouncementsScreen({
  navigation: drawer,
}: DrawerScreenProps<AppDrawerParamList, 'Announcements'>) {
  const stack = drawer.getParent<Stack>();
  const scope = useFamilyScope();
  const query = useInfiniteQuery({
    queryKey: ['announcements', scope.familyParam ?? 'own'],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => announcementsApi.list(pageParam, scope.familyParam),
    getNextPageParam: nextPage<Announcement>,
    enabled: Boolean(scope.familyId),
  });
  const items = query.data?.pages.flatMap((p) => p.items) ?? [];
  return (
    <Screen>
      {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
      {scope.isAdmin && scope.familyId ? (
        <Button title="Đăng thông báo" onPress={() => stack?.navigate('AnnouncementForm', {})} />
      ) : null}
      {query.isPending && scope.familyId ? <Loading label="Đang tải thông báo..." /> : null}
      <ErrorText error={query.error} />
      {!query.isPending && !query.isError && items.length === 0 ? (
        <EmptyPanel message="Chưa có thông báo gia đình." />
      ) : null}
      {items.map((item) => (
        <AnnouncementCard
          key={item.id}
          item={item}
          stack={stack}
          canManage={scope.isAdmin}
          canReport={!scope.isSystemAdmin}
        />
      ))}
      {query.hasNextPage ? (
        <Button
          title="Xem thêm thông báo"
          variant="secondary"
          loading={query.isFetchingNextPage}
          onPress={() => query.fetchNextPage()}
        />
      ) : null}
    </Screen>
  );
}

function AnnouncementCard({
  item,
  stack,
  canManage,
  canReport,
}: {
  item: Announcement;
  stack: Stack | undefined;
  canManage: boolean;
  canReport: boolean;
}) {
  const cache = useQueryClient();
  const [reporting, setReporting] = useState(false);
  const [reason, setReason] = useState('');
  const remove = useMutation({
    mutationFn: () => announcementsApi.remove(item.id),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['announcements'] }),
  });
  const report = useMutation({
    mutationFn: () => communityApi.report('ANNOUNCEMENT', item.id, reason.trim()),
    onSuccess: () => {
      setReporting(false);
      setReason('');
    },
  });
  return (
    <Card>
      {item.isPinned ? <Pill label="📌 Đã ghim" /> : null}
      <Text style={[text.label, { color: colors.textPrimary }]}>{item.title}</Text>
      <Text style={[text.body, { color: colors.textPrimary }]}>{item.content}</Text>
      <Text style={[text.caption, { color: colors.textSecondary }]}>
        {formatWhen(item.createdAt)}
      </Text>
      {canManage ? (
        <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
          <Button
            title="Sửa / ghim"
            variant="secondary"
            onPress={() => stack?.navigate('AnnouncementForm', { announcementId: item.id })}
          />
          <Button
            title="Xóa"
            variant="danger"
            loading={remove.isPending}
            onPress={() =>
              confirmAction('Xóa thông báo', 'Xóa thông báo này?', () => remove.mutate(), 'Xóa')
            }
          />
        </View>
      ) : null}
      {canReport ? (
        <Button title="Báo cáo" variant="secondary" onPress={() => setReporting(!reporting)} />
      ) : null}
      {reporting ? (
        <View style={{ gap: 8 }}>
          <TextArea label="Lý do báo cáo" maxLength={255} value={reason} onChangeText={setReason} />
          <Button
            title="Gửi báo cáo"
            loading={report.isPending}
            disabled={!reason.trim()}
            onPress={() => report.mutate()}
          />
        </View>
      ) : null}
      {report.isSuccess ? (
        <Text style={[text.body, { color: colors.success }]}>Đã gửi báo cáo.</Text>
      ) : null}
      <ErrorText error={remove.error ?? report.error} />
    </Card>
  );
}

export function AnnouncementFormScreen({ navigation, route }: StackProps<'AnnouncementForm'>) {
  const id = route.params.announcementId;
  const scope = useFamilyScope();
  const cache = useQueryClient();
  // Thông báo đang sửa lấy từ danh sách đã tải (API không có GET một thông báo).
  const existing = id
    ? cache
        .getQueriesData<{ pages: { items: Announcement[] }[] }>({ queryKey: ['announcements'] })
        .flatMap(([, data]) => data?.pages.flatMap((p) => p.items) ?? [])
        .find((a) => a.id === id)
    : undefined;
  const [title, setTitle] = useState(existing?.title ?? '');
  const [content, setContent] = useState(existing?.content ?? '');
  const [pinned, setPinned] = useState(existing?.isPinned ?? true);
  useEffect(() => {
    navigation.setOptions({ title: id ? 'Sửa thông báo' : 'Đăng thông báo' });
  }, [navigation, id]);
  const save = useMutation({
    mutationFn: () =>
      id
        ? announcementsApi.update(id, {
            title: title.trim(),
            content: content.trim(),
            isPinned: pinned,
          })
        : announcementsApi.create(
            { title: title.trim(), content: content.trim(), isPinned: pinned },
            scope.familyParam,
          ),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['announcements'] });
      navigation.goBack();
    },
  });
  if (id && !existing) {
    return (
      <Screen>
        <Notice
          variant="warning"
          title="Không tìm thấy"
          message="Mở lại danh sách thông báo rồi thử lại."
        />
      </Screen>
    );
  }
  return (
    <Screen>
      <Card>
        <TextField label="Tiêu đề" maxLength={255} value={title} onChangeText={setTitle} />
        <TextArea label="Nội dung" maxLength={5000} value={content} onChangeText={setContent} />
        <Checkbox label="Ghim ở đầu bảng tin" checked={pinned} onChange={setPinned} />
        <ErrorText error={save.error} />
        <Button
          title={id ? 'Lưu' : 'Đăng thông báo'}
          loading={save.isPending}
          disabled={!title.trim() || !content.trim()}
          onPress={() => save.mutate()}
        />
      </Card>
    </Screen>
  );
}

// ---------------------------------------------------------------- Di sản

/** Nút thêm nội dung trên màn Di sản (BR-HER-01: chỉ trưởng chi, trưởng họ, quản trị); người tiêu biểu ai cũng xem. */
export function HeritageActions({ stack }: { stack: Stack | undefined }) {
  const scope = useFamilyScope();
  const cache = useQueryClient();
  const [caption, setCaption] = useState('');
  const upload = useMutation({
    mutationFn: async () => {
      const file = await pickImage();
      if (file && scope.familyId) await heritageManageApi.upload(file, scope.familyId, caption);
    },
    onSuccess: async () => {
      setCaption('');
      await cache.invalidateQueries({ queryKey: ['heritage'] });
    },
  });
  return (
    <View style={{ gap: 8 }}>
      {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
      <Button
        title="Người tiêu biểu"
        variant="secondary"
        onPress={() => stack?.navigate('App', { screen: 'OutstandingMembers' })}
      />
      {scope.isAdmin && scope.familyId ? (
        <>
          <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
            <Button title="Thêm tài liệu" onPress={() => stack?.navigate('DocumentForm', {})} />
            <Button
              title="Viết câu chuyện"
              variant="secondary"
              onPress={() => stack?.navigate('StoryForm', {})}
            />
          </View>
          <TextField
            label="Chú thích ảnh (không bắt buộc)"
            maxLength={255}
            value={caption}
            onChangeText={setCaption}
          />
          <Button
            title="Tải ảnh lên thư viện"
            variant="secondary"
            loading={upload.isPending}
            onPress={() => upload.mutate()}
          />
          <ErrorText error={upload.error} />
        </>
      ) : null}
    </View>
  );
}

/**
 * Thêm tài liệu (tải tệp HERITAGE rồi tạo tài liệu) hoặc sửa tiêu đề, mô tả, danh mục.
 * shortcut: mobile chỉ chọn được ảnh (expo-image-picker); chọn tệp PDF cần thêm expo-document-picker (chưa duyệt).
 */
export function DocumentFormScreen({ navigation, route }: StackProps<'DocumentForm'>) {
  const id = route.params.documentId;
  const scope = useFamilyScope();
  const cache = useQueryClient();
  const existing = useQuery({
    queryKey: ['heritage', 'document', id],
    queryFn: () => heritageManageApi.document(id!),
    enabled: Boolean(id),
  });
  const [title, setTitle] = useState<string | null>(null);
  const [description, setDescription] = useState<string | null>(null);
  const [category, setCategory] = useState<string | null>(null);
  const [file, setFile] = useState<{ uri: string; name: string; mimeType: string } | null>(null);
  const t = title ?? existing.data?.title ?? '';
  const d = description ?? existing.data?.description ?? '';
  const c = category ?? existing.data?.category ?? '';
  useEffect(() => {
    navigation.setOptions({ title: id ? 'Sửa tài liệu' : 'Thêm tài liệu' });
  }, [navigation, id]);
  const save = useMutation({
    mutationFn: async () => {
      const body = { title: t.trim(), description: d.trim() || null, category: c.trim() || null };
      if (id) return heritageManageApi.updateDocument(id, body);
      if (!scope.familyId) throw new Error('Chưa chọn gia đình.');
      if (!file) throw new Error('Chọn tệp đính kèm trước khi lưu.');
      const media = await heritageManageApi.upload(file, scope.familyId);
      return heritageManageApi.createDocument({ ...body, mediaId: media.id }, scope.familyParam);
    },
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['heritage'] });
      navigation.goBack();
    },
  });
  if (id && existing.isPending) return <Loading />;
  return (
    <Screen>
      <Card>
        <TextField label="Tiêu đề" maxLength={200} value={t} onChangeText={setTitle} />
        <TextArea label="Mô tả" value={d} onChangeText={setDescription} />
        <TextField
          label="Danh mục"
          maxLength={50}
          placeholder="gia-pha, sac-phong..."
          value={c}
          onChangeText={setCategory}
        />
        {!id ? (
          <>
            <Text style={[text.label, { color: colors.textPrimary }]}>
              Tệp đính kèm (ảnh JPG, PNG, WEBP tối đa 5 MB)
            </Text>
            <Button
              title={file ? `Đã chọn: ${file.name}` : 'Chọn ảnh tài liệu'}
              variant="secondary"
              onPress={async () => setFile(await pickImage())}
            />
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              Tài liệu PDF (tối đa 10 MB) thêm trên bản web.
            </Text>
          </>
        ) : null}
        <ErrorText error={existing.error ?? save.error} />
        <Button
          title={save.isPending ? 'Đang tải và lưu...' : id ? 'Lưu' : 'Thêm tài liệu'}
          loading={save.isPending}
          disabled={!t.trim() || (!id && !file)}
          onPress={() => save.mutate()}
        />
      </Card>
    </Screen>
  );
}

/** Sửa, xóa tài liệu trên màn xem tài liệu (chỉ người quản lý, như web). */
export function DocumentManagePanel({
  documentId,
  stack,
  onDeleted,
}: {
  documentId: string;
  stack: Stack;
  onDeleted: () => void;
}) {
  const scope = useFamilyScope();
  const cache = useQueryClient();
  const remove = useMutation({
    mutationFn: () => heritageManageApi.deleteDocument(documentId),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['heritage'] });
      onDeleted();
    },
  });
  if (!scope.isAdmin) return null;
  return (
    <View style={{ gap: 8 }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button
          title="Sửa tài liệu"
          variant="secondary"
          onPress={() => stack.push('DocumentForm', { documentId })}
        />
        <Button
          title="Xóa tài liệu"
          variant="danger"
          loading={remove.isPending}
          onPress={() =>
            confirmAction('Xóa tài liệu', 'Xóa tài liệu này?', () => remove.mutate(), 'Xóa')
          }
        />
      </View>
      <ErrorText error={remove.error} />
    </View>
  );
}

/** Viết hoặc sửa câu chuyện gia đình; có thể gắn với một người trên cây. */
export function StoryFormScreen({ navigation, route }: StackProps<'StoryForm'>) {
  const id = route.params.storyId;
  const scope = useFamilyScope();
  const cache = useQueryClient();
  const existing = useQuery({
    queryKey: ['heritage', 'story', id],
    queryFn: () => heritageApi.story(id!),
    enabled: Boolean(id),
  });
  const people = useQuery({
    queryKey: ['members-options', scope.familyId],
    queryFn: () =>
      genealogyManageApi.persons(scope.familyId as string, {
        page: 0,
        size: 100,
        sort: 'fullName,asc',
      }),
    enabled: Boolean(scope.familyId),
  });
  const [title, setTitle] = useState<string | null>(null);
  const [content, setContent] = useState<string | null>(null);
  const [relatedPersonId, setRelatedPersonId] = useState<string | null>(null);
  const t = title ?? existing.data?.title ?? '';
  const c = content ?? existing.data?.content ?? '';
  const r = relatedPersonId ?? existing.data?.relatedPersonId ?? '';
  useEffect(() => {
    navigation.setOptions({ title: id ? 'Sửa câu chuyện' : 'Viết câu chuyện' });
  }, [navigation, id]);
  const save = useMutation({
    mutationFn: async () => {
      const body = { title: t.trim(), content: c.trim(), relatedPersonId: r || null };
      if (id) return heritageManageApi.updateStory(id, body);
      if (!scope.familyId) throw new Error('Chưa chọn gia đình.');
      return heritageManageApi.createStory(body, scope.familyParam);
    },
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['heritage'] });
      navigation.goBack();
    },
  });
  if (id && existing.isPending) return <Loading />;
  return (
    <Screen>
      <Card>
        <TextField label="Tiêu đề" maxLength={200} value={t} onChangeText={setTitle} />
        <TextArea label="Nội dung" value={c} onChangeText={setContent} />
        <Select
          label="Người liên quan (không bắt buộc)"
          value={r}
          onChange={setRelatedPersonId}
          options={[
            { value: '', label: 'Không gắn' },
            ...(people.data?.items ?? []).map((p) => ({
              value: p.id ?? '',
              label: p.fullName ?? '',
            })),
          ]}
        />
        <ErrorText error={existing.error ?? save.error} />
        <Button
          title={id ? 'Lưu' : 'Đăng câu chuyện'}
          loading={save.isPending}
          disabled={!t.trim() || !c.trim()}
          onPress={() => save.mutate()}
        />
      </Card>
    </Screen>
  );
}

/** Sửa, xóa câu chuyện: người quản lý hoặc chính tác giả (như web). */
export function StoryManagePanel({
  storyId,
  authorId,
  stack,
  onDeleted,
}: {
  storyId: string;
  authorId: string;
  stack: Stack;
  onDeleted: () => void;
}) {
  const scope = useFamilyScope();
  const cache = useQueryClient();
  const remove = useMutation({
    mutationFn: () => heritageManageApi.deleteStory(storyId),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['heritage'] });
      onDeleted();
    },
  });
  if (!scope.isAdmin && authorId !== scope.userId) return null;
  return (
    <View style={{ gap: 8 }}>
      <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
        <Button
          title="Sửa câu chuyện"
          variant="secondary"
          onPress={() => stack.push('StoryForm', { storyId })}
        />
        <Button
          title="Xóa câu chuyện"
          variant="danger"
          loading={remove.isPending}
          onPress={() =>
            confirmAction('Xóa câu chuyện', 'Xóa câu chuyện này?', () => remove.mutate(), 'Xóa')
          }
        />
      </View>
      <ErrorText error={remove.error} />
    </View>
  );
}

/** FR-HER-03: thành viên tiêu biểu; người quản lý thêm, sửa thành tích, thứ tự, xóa (BR-HER-04). */
export function OutstandingMembersScreen({
  navigation: drawer,
}: DrawerScreenProps<AppDrawerParamList, 'OutstandingMembers'>) {
  const stack = drawer.getParent<Stack>();
  const scope = useFamilyScope();
  const cache = useQueryClient();
  const [page, setPage] = useState(0);
  const list = useQuery({
    queryKey: ['outstanding-members', scope.familyId, page],
    queryFn: () => heritageManageApi.outstandingMembers(page, scope.familyParam),
    enabled: Boolean(scope.familyId),
  });
  const remove = useMutation({
    mutationFn: (id: string) => heritageManageApi.deleteOutstandingMember(id),
    onSuccess: () => cache.invalidateQueries({ queryKey: ['outstanding-members'] }),
  });
  return (
    <Screen>
      <Text style={[text.body, { color: colors.textSecondary }]}>
        Những người con tiêu biểu của dòng họ.
      </Text>
      {scope.isSystemAdmin ? <AdminFamilyPicker /> : null}
      {scope.isAdmin && scope.familyId ? (
        <Button
          title="Thêm người tiêu biểu"
          onPress={() => stack?.navigate('OutstandingForm', {})}
        />
      ) : null}
      {list.isPending && scope.familyId ? <Loading /> : null}
      <ErrorText error={list.error ?? remove.error} />
      {list.isSuccess && list.data.items.length === 0 ? (
        <EmptyPanel message="Chưa có người tiêu biểu." />
      ) : null}
      {list.data?.items.map((m: OutstandingMember) => (
        <Card key={m.id}>
          <Text style={[text.label, { color: colors.textPrimary }]}>{m.fullName}</Text>
          <Text style={[text.body, { color: colors.textPrimary }]}>{m.achievement}</Text>
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            Thứ tự hiển thị: {m.displayOrder}
          </Text>
          <Button
            title="Xem hồ sơ"
            variant="secondary"
            onPress={() => stack?.navigate('PersonDetail', { personId: m.personId })}
          />
          {scope.isAdmin ? (
            <View style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}>
              <Button
                title="Sửa"
                variant="secondary"
                onPress={() => stack?.navigate('OutstandingForm', { memberId: m.id })}
              />
              <Button
                title="Xóa"
                variant="danger"
                loading={remove.isPending && remove.variables === m.id}
                onPress={() =>
                  confirmAction(
                    'Xóa người tiêu biểu',
                    `Bỏ ${m.fullName} khỏi danh sách tiêu biểu?`,
                    () => remove.mutate(m.id),
                    'Xóa',
                  )
                }
              />
            </View>
          ) : null}
        </Card>
      ))}
      <Pager page={page} total={list.data?.meta.total ?? 0} pageSize={20} onChange={setPage} />
    </Screen>
  );
}

export function OutstandingFormScreen({ navigation, route }: StackProps<'OutstandingForm'>) {
  const id = route.params.memberId;
  const scope = useFamilyScope();
  const cache = useQueryClient();
  // Người đang sửa lấy từ danh sách đã tải (API không có GET một người tiêu biểu).
  const existing = id
    ? cache
        .getQueriesData<{ items: OutstandingMember[] }>({ queryKey: ['outstanding-members'] })
        .flatMap(([, data]) => data?.items ?? [])
        .find((m) => m.id === id)
    : undefined;
  const people = useQuery({
    queryKey: ['members-options', scope.familyId],
    queryFn: () =>
      genealogyManageApi.persons(scope.familyId as string, {
        page: 0,
        size: 100,
        sort: 'fullName,asc',
      }),
    enabled: Boolean(scope.familyId) && !id,
  });
  const [personId, setPersonId] = useState('');
  const [achievement, setAchievement] = useState(existing?.achievement ?? '');
  const [displayOrder, setDisplayOrder] = useState(String(existing?.displayOrder ?? 0));
  const badOrder = !/^\d{1,4}$/.test(displayOrder);
  useEffect(() => {
    navigation.setOptions({ title: id ? 'Sửa người tiêu biểu' : 'Thêm người tiêu biểu' });
  }, [navigation, id]);
  const save = useMutation({
    mutationFn: () =>
      id
        ? heritageManageApi.updateOutstandingMember(id, {
            achievement: achievement.trim(),
            displayOrder: Number(displayOrder),
          })
        : heritageManageApi.createOutstandingMember(
            { personId, achievement: achievement.trim(), displayOrder: Number(displayOrder) },
            scope.familyParam,
          ),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['outstanding-members'] });
      navigation.goBack();
    },
  });
  return (
    <Screen>
      <Card>
        {id ? (
          <Text style={[text.label, { color: colors.textPrimary }]}>{existing?.fullName}</Text>
        ) : (
          <Select
            label="Thành viên"
            value={personId}
            onChange={setPersonId}
            placeholder="Chọn thành viên"
            options={(people.data?.items ?? []).map((p) => ({
              value: p.id ?? '',
              label: p.fullName ?? '',
            }))}
          />
        )}
        <TextArea label="Thành tích" value={achievement} onChangeText={setAchievement} />
        <TextField
          label="Thứ tự hiển thị"
          keyboardType="number-pad"
          maxLength={4}
          value={displayOrder}
          onChangeText={setDisplayOrder}
          error={badOrder ? 'Nhập số từ 0' : undefined}
        />
        <ErrorText error={people.error ?? save.error} />
        <Button
          title="Lưu"
          loading={save.isPending}
          disabled={(!id && !personId) || !achievement.trim() || badOrder}
          onPress={() => save.mutate()}
        />
      </Card>
    </Screen>
  );
}
