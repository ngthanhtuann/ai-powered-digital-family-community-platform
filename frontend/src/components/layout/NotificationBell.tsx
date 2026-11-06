'use client';

import { Bell } from 'lucide-react';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { notificationsApi } from '@/services/notifications';

/** Số chưa đọc dùng meta.total của bộ lọc isRead=false, không chỉ đếm 20 dòng trang đầu. */
export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const cache = useQueryClient();
  const unread = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: () => notificationsApi.list(0, false),
    refetchInterval: 60_000,
  });
  const all = useQuery({
    queryKey: ['notifications', 'recent'],
    queryFn: () => notificationsApi.list(),
    enabled: open,
  });
  const mark = useMutation({
    mutationFn: (id: string) => notificationsApi.markRead(id),
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['notifications'] });
    },
  });
  return (
    <div className="relative">
      <button
        type="button"
        aria-label="Thông báo"
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
        className="relative inline-flex min-h-11 min-w-11 items-center justify-center rounded-full border border-border hover:bg-surface-muted"
      >
        <Bell size={20} aria-hidden="true" />
        {(unread.data?.meta.total ?? 0) > 0 && (
          <span
            className="absolute -right-1 -top-1 rounded-full bg-error px-1.5 py-0.5 text-caption text-white"
            aria-label={`${unread.data!.meta.total} thông báo chưa đọc`}
          >
            {unread.data!.meta.total > 99 ? '99+' : unread.data!.meta.total}
          </span>
        )}
      </button>
      {open && (
        <section
          aria-label="Thông báo của bạn"
          className="absolute right-0 z-50 mt-2 w-[min(22rem,calc(100vw-2rem))] rounded-card border border-border bg-surface p-3 text-left shadow-lg"
        >
          <h2 className="mb-3 font-semibold">Thông báo</h2>
          {(all.isPending || unread.isPending) && <p className="text-body">Đang tải...</p>}
          {(all.isError || unread.isError) && (
            <p role="alert" className="text-body text-error">
              Không tải được thông báo.
            </p>
          )}
          <ul className="max-h-96 space-y-2 overflow-y-auto">
            {all.data?.items.map((item) => (
              <li
                key={item.id}
                className={
                  'rounded-control p-3 text-body ' +
                  (item.isRead ? 'bg-surface-muted' : 'border border-primary')
                }
              >
                <p className="font-semibold">{item.title}</p>
                <p className="mt-1 whitespace-pre-wrap">{item.content}</p>
                <p className="mt-1 text-caption text-text-secondary">
                  {new Date(item.createdAt).toLocaleString('vi-VN')}
                </p>
                {!item.isRead && (
                  <button
                    type="button"
                    className="mt-2 min-h-9 text-body underline"
                    disabled={mark.isPending}
                    onClick={() => mark.mutate(item.id)}
                  >
                    Đánh dấu đã đọc
                  </button>
                )}
              </li>
            ))}
          </ul>
          {all.data?.items.length === 0 && (
            <p className="text-body text-text-secondary">Chưa có thông báo.</p>
          )}
          {all.data && all.data.meta.total > all.data.items.length && (
            <p className="mt-2 text-caption text-text-secondary">
              Đang hiển thị 20 thông báo mới nhất.
            </p>
          )}
          {mark.isError && (
            <p role="alert" className="mt-2 text-body text-error">
              Không đánh dấu được đã đọc.
            </p>
          )}
        </section>
      )}
    </div>
  );
}
