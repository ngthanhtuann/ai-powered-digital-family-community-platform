'use client';

import { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm, type FieldErrors, type UseFormRegister } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { eventsApi, type Event, type EventRequest, type RsvpStatus } from '@/services/events/events';

const inputClass = 'w-full rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';
const errorText = (error: unknown) => error instanceof Error ? error.message : 'Có lỗi xảy ra.';
const formatTime = (value: string) => new Date(value).toLocaleString('vi-VN');
const pad = (value: number) => String(value).padStart(2, '0');
const localDateTime = (value: string) => {
  const d = new Date(value);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
};
const iso = (value: string) => value ? new Date(value).toISOString() : undefined;
const statusNames: Record<RsvpStatus, string> = {
  GOING: 'Tham dự', NOT_GOING: 'Không tham dự', MAYBE: 'Có thể',
};

// Cùng giới hạn với CreateEvent ở backend; backend vẫn kiểm tra lại.
const eventSchema = z.object({
  title: z.string().trim().min(1, 'Tên sự kiện không được để trống').max(255, 'Tối đa 255 ký tự'),
  start: z.string().min(1, 'Chọn thời gian bắt đầu'),
  end: z.string(),
  location: z.string().trim().max(255, 'Tối đa 255 ký tự'),
  description: z.string().max(5000, 'Tối đa 5000 ký tự'),
}).refine((value) => !value.end || new Date(value.end) > new Date(value.start), {
  path: ['end'], message: 'Kết thúc phải sau thời điểm bắt đầu',
});
type EventValues = z.infer<typeof eventSchema>;
const emptyEvent: EventValues = { title: '', start: '', end: '', location: '', description: '' };

function EventFields({ idPrefix, register, errors }: {
  idPrefix: string; register: UseFormRegister<EventValues>; errors: FieldErrors<EventValues>;
}) {
  const errorId = (name: keyof EventValues) => `${idPrefix}-${name}-error`;
  const a11y = (name: keyof EventValues) => ({
    'aria-invalid': Boolean(errors[name]),
    'aria-describedby': errors[name] ? errorId(name) : undefined,
  });
  const message = (name: keyof EventValues) => errors[name]?.message &&
    <p id={errorId(name)} role="alert" className="text-sm text-error">{errors[name]?.message}</p>;
  return <>
    <label htmlFor={`${idPrefix}-title`} className="block text-sm">Tên sự kiện</label>
    <input id={`${idPrefix}-title`} className={inputClass} maxLength={255} placeholder="Tên sự kiện"
      {...a11y('title')} {...register('title')} />
    {message('title')}
    <label htmlFor={`${idPrefix}-start`} className="block text-sm">Thời gian bắt đầu</label>
    <input id={`${idPrefix}-start`} className={inputClass} type="datetime-local" {...a11y('start')} {...register('start')} />
    {message('start')}
    <label htmlFor={`${idPrefix}-end`} className="block text-sm">Thời gian kết thúc (không bắt buộc)</label>
    <input id={`${idPrefix}-end`} className={inputClass} type="datetime-local" {...a11y('end')} {...register('end')} />
    {message('end')}
    <label htmlFor={`${idPrefix}-location`} className="block text-sm">Địa điểm</label>
    <input id={`${idPrefix}-location`} className={inputClass} maxLength={255} {...a11y('location')} {...register('location')} />
    {message('location')}
    <label htmlFor={`${idPrefix}-description`} className="block text-sm">Mô tả</label>
    <textarea id={`${idPrefix}-description`} className={inputClass} rows={3} maxLength={5000}
      {...a11y('description')} {...register('description')} />
    {message('description')}
  </>;
}

function Participants({ eventId }: { eventId: string }) {
  const [filter, setFilter] = useState<RsvpStatus | ''>('');
  const query = useInfiniteQuery({
    queryKey: ['event-participants', eventId, filter],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => eventsApi.participants(eventId,pageParam,filter || undefined),
    getNextPageParam: (last) =>
      (last.meta.page + 1) * last.meta.size < last.meta.total ? last.meta.page + 1 : undefined,
  });
  const people = query.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <section className="mt-4 rounded-lg bg-surface-muted p-3" aria-label="Danh sách người tham dự">
      <label htmlFor={'filter-' + eventId} className="block text-sm font-medium">Lọc người tham dự</label>
      <select id={'filter-' + eventId} className={inputClass} value={filter}
        onChange={(e) => setFilter(e.target.value as RsvpStatus | '')}>
        <option value="">Tất cả trạng thái</option>
        {Object.entries(statusNames).map(([value,label]) => <option key={value} value={value}>{label}</option>)}
      </select>
      {query.isPending && <p className="mt-2 text-sm">Đang tải danh sách...</p>}
      {query.isError && <p role="alert" className="mt-2 text-error">{errorText(query.error)}</p>}
      <ul className="mt-2 space-y-2">
        {people.map((person) => <li key={person.id} className="flex justify-between gap-2 text-sm">
          <span>{person.displayName}</span>
          <span className="text-text-secondary">{statusNames[person.rsvpStatus]}</span>
        </li>)}
      </ul>
      {!query.isPending && !query.isError && people.length === 0 &&
        <p className="mt-2 text-sm text-text-secondary">Chưa có người tham dự.</p>}
      {query.hasNextPage && <Button variant="secondary" loading={query.isFetchingNextPage}
        onClick={() => query.fetchNextPage()}>Xem thêm</Button>}
    </section>
  );
}

function EventCard({ event, userId, admin }: { event: Event; userId?: string; admin: boolean }) {
  const cache = useQueryClient();
  const [showParticipants, setShowParticipants] = useState(false);
  const [editing, setEditing] = useState(false);
  const editForm = useForm<EventValues>({
    resolver: zodResolver(eventSchema),
    defaultValues: {
      title: event.title, description: event.description ?? '', start: localDateTime(event.startAt),
      end: event.endAt ? localDateTime(event.endAt) : '', location: event.location ?? '',
    },
  });
  const canManage = event.createdBy === userId || admin;
  const started = new Date(event.startAt).getTime() <= Date.now();
  const refresh = async () => {
    await Promise.all([
      cache.invalidateQueries({ queryKey: ['events'] }),
      cache.invalidateQueries({ queryKey: ['event-participants', event.id] }),
    ]);
  };
  const rsvp = useMutation({
    mutationFn: (status: RsvpStatus) => eventsApi.rsvp(event.id,status), onSuccess: refresh,
  });
  const update = useMutation({
    mutationFn: (values: EventValues) => eventsApi.update(event.id, {
      title: values.title, description: values.description, startAt: iso(values.start)!,
      endAt: iso(values.end) ?? null, location: values.location,
    }),
    onSuccess: async () => { setEditing(false); await refresh(); },
  });
  const remove = useMutation({
    mutationFn: () => eventsApi.remove(event.id), onSuccess: refresh,
  });

  return (
    <article className="space-y-3 rounded-xl border border-border bg-surface p-5 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 className="font-semibold">{event.title}</h3>
          <p className="text-sm text-text-secondary">Bắt đầu: {formatTime(event.startAt)}</p>
          {event.endAt && <p className="text-sm text-text-secondary">Kết thúc: {formatTime(event.endAt)}</p>}
          {event.location && <p className="text-sm text-text-secondary">Địa điểm: {event.location}</p>}
        </div>
        {canManage && <div className="flex gap-2">
          <Button variant="secondary" fullWidth={false} onClick={() => setEditing(!editing)}>Sửa</Button>
          <Button variant="danger" fullWidth={false} loading={remove.isPending}
            onClick={() => { if (window.confirm('Hủy sự kiện này?')) remove.mutate(); }}>Hủy sự kiện</Button>
        </div>}
      </div>
      {event.description && <p className="whitespace-pre-wrap break-words">{event.description}</p>}
      {editing && <form className="space-y-2" noValidate
        onSubmit={editForm.handleSubmit((values) => update.mutate(values))}>
        <EventFields idPrefix={'edit-' + event.id} register={editForm.register}
          errors={editForm.formState.errors} />
        <div className="flex gap-2">
          <Button type="submit" fullWidth={false} loading={update.isPending}>Lưu</Button>
          <Button variant="secondary" fullWidth={false} onClick={() => setEditing(false)}>Đóng</Button>
        </div>
        {update.isError && <p role="alert" className="text-error">{errorText(update.error)}</p>}
      </form>}
      <div className="flex flex-wrap gap-2" role="group" aria-label="Phản hồi tham dự">
        {(Object.entries(statusNames) as [RsvpStatus,string][]).map(([status,label]) =>
          <button key={status} type="button" disabled={started || rsvp.isPending}
            aria-pressed={event.myRsvpStatus === status}
            className={'min-h-11 rounded-full border px-3 py-2 text-sm disabled:opacity-60 ' +
              (event.myRsvpStatus === status ? 'border-primary bg-surface-muted font-semibold' : 'border-border')}
            onClick={() => rsvp.mutate(status)}>{label}</button>)}
      </div>
      <p className="text-sm text-text-secondary">Tham dự: {event.rsvpCounts.going} · Không: {event.rsvpCounts.notGoing} · Có thể: {event.rsvpCounts.maybe}</p>
      {started && <p className="text-sm text-text-secondary">Sự kiện đã bắt đầu, không thể thay đổi RSVP.</p>}
      {rsvp.isError && <p role="alert" className="text-error">{errorText(rsvp.error)}</p>}
      {remove.isError && <p role="alert" className="text-error">{errorText(remove.error)}</p>}
      <button type="button" className="min-h-11 text-sm underline"
        onClick={() => setShowParticipants(!showParticipants)}>
        {showParticipants ? 'Ẩn người tham dự' : 'Xem danh sách người tham dự'}
      </button>
      {showParticipants && <Participants eventId={event.id} />}
    </article>
  );
}

export default function EventsPage() {
  const cache = useQueryClient();
  const [tab, setTab] = useState<'UPCOMING' | 'PAST'>('UPCOMING');
  const [cutoff, setCutoff] = useState(() => new Date().toISOString());
  const [adminFamily, setAdminFamily] = useState('');
  const createForm = useForm<EventValues>({ resolver: zodResolver(eventSchema), defaultValues: emptyEvent });
  const membership = useQuery({ queryKey: ['me-membership'], queryFn: eventsApi.membership });
  const me = useQuery({ queryKey: ['me-user'], queryFn: eventsApi.me });
  const systemAdmin = me.data?.roles.includes('SYSTEM_ADMIN') ?? false;
  const branchAdmin = membership.data?.role === 'BRANCH_ADMIN';
  const familyId = systemAdmin ? adminFamily.trim() : undefined;
  const ready = systemAdmin ? Boolean(familyId) : membership.data?.status === 'APPROVED';
  const feed = useInfiniteQuery({
    queryKey: ['events',tab,cutoff,familyId ?? 'own'],
    enabled: ready,
    initialPageParam: 0,
    queryFn: ({ pageParam }) => eventsApi.list(pageParam,
      tab === 'UPCOMING' ? cutoff : undefined,
      tab === 'PAST' ? cutoff : undefined,familyId),
    getNextPageParam: (last) =>
      (last.meta.page + 1) * last.meta.size < last.meta.total ? last.meta.page + 1 : undefined,
  });
  const create = useMutation({
    mutationFn: (values: EventValues) => {
      const body: EventRequest = {
        title: values.title, startAt: iso(values.start)!,
        ...(values.description ? { description: values.description } : {}),
        ...(values.end ? { endAt: iso(values.end) } : {}),
        ...(values.location ? { location: values.location } : {}),
      };
      return eventsApi.create(body,familyId);
    },
    onSuccess: async () => {
      createForm.reset(emptyEvent);
      setCutoff(new Date().toISOString());
      await cache.invalidateQueries({ queryKey: ['events'] });
    },
  });
  const items = feed.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <main className="mx-auto max-w-3xl space-y-6 px-4 py-8">
      <header>
        <h1 className="text-page-title">Sự kiện gia đình</h1>
        <p className="mt-1 text-text-secondary">Tạo sự kiện, phản hồi tham dự và xem danh sách người tham dự.</p>
      </header>
      {(membership.isPending || me.isPending) && <p>Đang kiểm tra quyền thành viên...</p>}
      {(membership.isError || me.isError) && <p role="alert" className="text-error">
        {errorText(membership.error || me.error)}
      </p>}
      {!systemAdmin && membership.data && membership.data.status !== 'APPROVED' &&
        <p>Bạn cần được xác minh thành viên để xem sự kiện.</p>}
      {systemAdmin && <div className="rounded-lg border border-border bg-surface p-4">
        <TextField id="event-family" label="Mã gia đình" value={adminFamily}
          onChange={(e) => setAdminFamily(e.target.value)} placeholder="UUID gia đình" />
      </div>}
      {ready && <>
        <form className="space-y-3 rounded-xl border border-border bg-surface p-5" noValidate
          onSubmit={createForm.handleSubmit((values) => create.mutate(values))}>
          <h2 className="font-semibold">Tạo sự kiện</h2>
          <EventFields idPrefix="event" register={createForm.register} errors={createForm.formState.errors} />
          <Button type="submit" fullWidth={false} loading={create.isPending}>
            Tạo sự kiện
          </Button>
          {create.isError && <p role="alert" className="text-error">{errorText(create.error)}</p>}
        </form>
        <nav aria-label="Lọc sự kiện" className="flex border-b border-border">
          {([{ value: 'UPCOMING', label: 'Sắp tới' }, { value: 'PAST', label: 'Đã qua' }] as const)
            .map(({ value,label }) => <button key={value} type="button"
              aria-current={tab === value ? 'page' : undefined}
              className={'border-b-2 px-4 py-3 font-medium ' +
                (tab === value ? 'border-primary text-primary' : 'border-transparent text-text-secondary')}
              onClick={() => { setTab(value); setCutoff(new Date().toISOString()); }}>{label}</button>)}
        </nav>
        <section className="space-y-4" aria-label="Danh sách sự kiện">
          {feed.isPending && <p>Đang tải sự kiện...</p>}
          {feed.isError && <p role="alert" className="text-error">{errorText(feed.error)}</p>}
          {!feed.isPending && !feed.isError && items.length === 0 &&
            <p className="rounded-lg bg-surface p-5 text-text-secondary">Chưa có sự kiện.</p>}
          {items.map((event) => <EventCard key={event.id} event={event} userId={me.data?.id}
            admin={branchAdmin || systemAdmin} />)}
          {feed.hasNextPage && <Button variant="secondary" loading={feed.isFetchingNextPage}
            onClick={() => feed.fetchNextPage()}>Xem thêm</Button>}
        </section>
      </>}
    </main>
  );
}
