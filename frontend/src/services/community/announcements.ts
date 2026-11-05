import { api } from '@/lib/api';
import type { components as Community } from '@/services/generated/community-events';

export type Announcement = Community['schemas']['Announcement'];
export type AnnouncementRequest = Community['schemas']['AnnouncementRequest'];
export type AnnouncementUpdateRequest = Community['schemas']['AnnouncementUpdateRequest'];

export const announcementsApi = {
  list: (page: number, familyId?: string) => api.list<Announcement>('/announcements', {
    page, size: 20, sort: 'createdAt,desc', ...(familyId ? { familyId } : {}),
  }),
  create: (body: AnnouncementRequest, familyId?: string) =>
    api.post<Announcement>('/announcements' + (familyId ? '?familyId=' + encodeURIComponent(familyId) : ''),body),
  update: (id: string, body: AnnouncementUpdateRequest) =>
    api.patch<Announcement>('/announcements/' + id,body),
  remove: (id: string) => api.delete('/announcements/' + id),
};
