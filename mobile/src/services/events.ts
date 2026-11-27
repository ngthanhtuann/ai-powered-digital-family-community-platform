import { api } from './api';
import type { components } from './generated/community-events';

type Schemas = components['schemas'];
export type Event = Schemas['Event'];
export type Participant = Schemas['Participant'];
export type EventRsvp = Schemas['RsvpRequest']['rsvpStatus'];
export type EventMedia = Schemas['Media'];
export const RSVP_LABEL: Record<EventRsvp, string> = {
  GOING: 'Tham dự',
  NOT_GOING: 'Không',
  MAYBE: 'Có thể',
};

export const eventsApi = {
  list: (page: number, period: 'upcoming' | 'past', now: string, familyId?: string) =>
    api.list<Event>('/events', {
      page,
      size: 20,
      sort: period === 'upcoming' ? 'startAt,asc' : 'startAt,desc',
      ...(period === 'upcoming' ? { from: now } : { to: now }),
      ...(familyId ? { familyId } : {}),
    }),
  detail: (id: string) => api.get<Event>(`/events/${encodeURIComponent(id)}`),
  rsvp: (id: string, rsvpStatus: EventRsvp) =>
    api.put<Participant>(`/events/${encodeURIComponent(id)}/rsvp`, {
      rsvpStatus,
    } satisfies Schemas['RsvpRequest']),
  participants: (id: string, page: number) =>
    api.list<Participant>(`/events/${encodeURIComponent(id)}/participants`, {
      page,
      size: 20,
      sort: 'createdAt,desc',
    }),
  media: (id: string, page: number) =>
    api.list<EventMedia>(`/events/${encodeURIComponent(id)}/media`, {
      page,
      size: 20,
      sort: 'createdAt,desc',
    }),
};

export type EventRequest = Schemas['EventRequest'];
export type EventUpdateRequest = Schemas['EventUpdateRequest'];

/** Tạo, sửa, xóa sự kiện; điểm danh; tải ảnh sự kiện (UC-EVT-01 đến 04), như web. */
export const eventManageApi = {
  create: (body: EventRequest, familyId?: string) =>
    api.post<Event>(`/events${familyId ? `?familyId=${encodeURIComponent(familyId)}` : ''}`, body),
  update: (id: string, body: EventUpdateRequest) =>
    api.patch<Event>(`/events/${encodeURIComponent(id)}`, body),
  remove: (id: string) => api.delete(`/events/${encodeURIComponent(id)}`),
  participants: (id: string, page: number, rsvpStatus?: EventRsvp) =>
    api.list<Participant>(`/events/${encodeURIComponent(id)}/participants`, {
      page,
      size: 20,
      sort: 'createdAt,desc',
      ...(rsvpStatus ? { rsvpStatus } : {}),
    }),
  updateAttendance: (eventId: string, participantId: string, attended: boolean) =>
    api.put<Participant>(
      `/events/${encodeURIComponent(eventId)}/participants/${encodeURIComponent(participantId)}/attendance`,
      { attended },
    ),
  uploadMedia: (id: string, file: { uri: string; name: string; mimeType: string }) => {
    const form = new FormData();
    form.append('file', { uri: file.uri, name: file.name, type: file.mimeType } as unknown as Blob);
    return api.upload<EventMedia>(`/events/${encodeURIComponent(id)}/media`, form);
  },
};
