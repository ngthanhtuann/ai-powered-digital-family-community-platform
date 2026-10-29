import { api } from '@/lib/api';
import type { components as Community } from '@/services/generated/community-events';
import type { components as Auth } from '@/services/generated/auth';

export type Event = Community['schemas']['Event'];
export type EventRequest = Community['schemas']['EventRequest'];
export type EventUpdateRequest = Community['schemas']['EventUpdateRequest'];
export type Participant = Community['schemas']['Participant'];
export type RsvpStatus = Community['schemas']['RsvpRequest']['rsvpStatus'];
export type User = Auth['schemas']['User'];
export type Membership = Auth['schemas']['Membership'];

export const eventsApi = {
  me: () => api.get<User>('/users/me'),
  membership: () => api.get<Membership>('/users/me/membership'),
  list: (page: number, from?: string, to?: string, familyId?: string) =>
    api.list<Event>('/events', {
      page, size: 20, sort: from ? 'startAt,asc' : 'startAt,desc',
      ...(from ? { from } : {}),
      ...(to ? { to } : {}),
      ...(familyId ? { familyId } : {}),
    }),
  detail: (id: string) => api.get<Event>('/events/' + id),
  create: (body: EventRequest, familyId?: string) =>
    api.post<Event>('/events' + (familyId ? '?familyId=' + encodeURIComponent(familyId) : ''), body),
  update: (id: string, body: EventUpdateRequest) => api.patch<Event>('/events/' + id, body),
  remove: (id: string) => api.delete('/events/' + id),
  rsvp: (id: string, rsvpStatus: RsvpStatus) =>
    api.put<Participant>('/events/' + id + '/rsvp', { rsvpStatus }),
  participants: (id: string, page: number, rsvpStatus?: RsvpStatus) =>
    api.list<Participant>('/events/' + id + '/participants', {
      page, size: 20, sort: 'createdAt,desc',
      ...(rsvpStatus ? { rsvpStatus } : {}),
    }),
};
