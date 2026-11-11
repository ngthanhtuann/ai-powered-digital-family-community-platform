import { api } from './api';
import type { components } from './generated/community-events';

type Schemas = components['schemas'];
export type Event = Schemas['Event'];
export type Participant = Schemas['Participant'];
export type EventRsvp = Schemas['RsvpRequest']['rsvpStatus'];
export type EventMedia = Schemas['Media'];

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
