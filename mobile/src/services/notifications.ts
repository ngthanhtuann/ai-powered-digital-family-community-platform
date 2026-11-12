import { api } from './api';
import type { components } from './generated/community-events';

export type Notification = components['schemas']['Notification'];

export const notificationsApi = {
  list: (page: number) =>
    api.list<Notification>('/notifications', { page, size: 20, sort: 'createdAt,desc' }),
  unreadCount: async () => {
    const result = await api.list<Notification>('/notifications', {
      page: 0,
      size: 1,
      sort: 'createdAt,desc',
      isRead: false,
    });
    return result.meta.total;
  },
  markRead: (id: string) =>
    api.patch<Notification>(`/notifications/${encodeURIComponent(id)}`, {
      isRead: true,
    } satisfies components['schemas']['MarkReadRequest']),
};
