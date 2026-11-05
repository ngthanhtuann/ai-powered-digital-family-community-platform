import { api } from '@/lib/api';
import type { components as Community } from '@/services/generated/community-events';

export type Notification = Community['schemas']['Notification'];

export const notificationsApi = {
  list: (page = 0, isRead?: boolean) => api.list<Notification>('/notifications', {
    page, size: 20, sort: 'createdAt,desc', ...(isRead === undefined ? {} : { isRead }),
  }),
  markRead: (id: string) => api.patch<Notification>('/notifications/' + id, { isRead: true }),
};
