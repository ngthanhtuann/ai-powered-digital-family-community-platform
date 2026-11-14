'use client';

import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { announcementsApi, type Announcement } from '@/services/community/announcements';
import { communityApi } from '@/services/community/posts';

const inputClass =
  'min-w-0 w-full rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body';

function AnnouncementCard({
  item,
  canManage,
  canReport,
}: {
  item: Announcement;
  canManage: boolean;
  canReport: boolean;
}) {
  const cache = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState(item.title);
  const [content, setContent] = useState(item.content);
  const [pinned, setPinned] = useState(item.isPinned);
  const [reporting, setReporting] = useState(false);
  const [reason, setReason] = useState('');
  const refresh = () => cache.invalidateQueries({ queryKey: ['announcements'] });
  const update = useMutation({
    mutationFn: () => announcementsApi.update(item.id, { title, content, isPinned: pinned }),
    onSuccess: async () => {
      setEditing(false);
      await refresh();
    },
  });
  const remove = useMutation({
    mutationFn: () => announcementsApi.remove(item.id),
    onSuccess: refresh,
  });
  const report = useMutation({
    mutationFn: () => communityApi.report('ANNOUNCEMENT', item.id, reason),
    onSuccess: () => {
      setReporting(false);
      setReason('');
    },
  });
  return (
    <article className="min-w-0 space-y-2 rounded-card border border-border bg-surface p-4">
      <div className="flex min-w-0 flex-wrap items-center gap-2">
        {item.isPinned && (
          <span className="rounded-full bg-surface-muted px-2 py-1 text-caption font-semibold">
            📌 Đã ghim
          </span>
        )}
        <h3 className="min-w-0 break-words font-semibold">{item.title}</h3>
      </div>
      <p className="whitespace-pre-wrap break-words">{item.content}</p>
      <p className="text-caption text-text-secondary">
        {new Date(item.createdAt).toLocaleString('vi-VN')}
      </p>
      {canManage && (
        <div className="flex flex-wrap gap-2">
          <Button variant="secondary" fullWidth={false} onClick={() => setEditing(!editing)}>
            Sửa / ghim
          </Button>
          <Button
            variant="danger"
            fullWidth={false}
            loading={remove.isPending}
            onClick={() => {
              if (window.confirm('Xóa thông báo này?')) remove.mutate();
            }}
          >
            Xóa
          </Button>
        </div>
      )}
      {editing && (
        <form
          className="space-y-2"
          onSubmit={(e) => {
            e.preventDefault();
            update.mutate();
          }}
        >
          <input
            className={inputClass}
            value={title}
            maxLength={255}
            required
            onChange={(e) => setTitle(e.target.value)}
            aria-label="Tiêu đề thông báo"
          />
          <textarea
            className={inputClass}
            value={content}
            maxLength={5000}
            required
            onChange={(e) => setContent(e.target.value)}
            aria-label="Nội dung thông báo"
          />
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={pinned} onChange={(e) => setPinned(e.target.checked)} />{' '}
            Ghim đầu bảng tin
          </label>
          <Button
            type="submit"
            fullWidth={false}
            loading={update.isPending}
            disabled={!title.trim() || !content.trim()}
          >
            Lưu
          </Button>
        </form>
      )}
      {canReport && (
        <button
          type="button"
          className="min-h-9 text-body underline"
          onClick={() => setReporting(!reporting)}
        >
          Báo cáo
        </button>
      )}
      {reporting && (
        <form
          className="space-y-2"
          onSubmit={(e) => {
            e.preventDefault();
            report.mutate();
          }}
        >
          <textarea
            aria-label="Lý do báo cáo"
            className={inputClass}
            maxLength={255}
            required
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <Button
            type="submit"
            fullWidth={false}
            loading={report.isPending}
            disabled={!reason.trim()}
          >
            Gửi báo cáo
          </Button>
        </form>
      )}
      {(update.isError || remove.isError || report.isError) && (
        <p role="alert" className="text-error">
          Thao tác thất bại. Vui lòng kiểm tra quyền và thử lại.
        </p>
      )}
      {report.isSuccess && (
        <p role="status" className="text-body">
          Đã gửi báo cáo.
        </p>
      )}
    </article>
  );
}

/** Luôn đặt thông báo ghim trước bài đăng trong bảng tin. */
export function Announcements({
  familyId,
  branchId,
  canManage,
  canReport,
}: {
  familyId?: string;
  branchId?: string;
  canManage: boolean;
  canReport: boolean;
}) {
  const cache = useQueryClient();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [pinned, setPinned] = useState(true);
  const query = useInfiniteQuery({
    queryKey: ['announcements', familyId ?? 'own'],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => announcementsApi.list(pageParam, familyId),
    getNextPageParam: (last) =>
      (last.meta.page + 1) * last.meta.size < last.meta.total ? last.meta.page + 1 : undefined,
  });
  const create = useMutation({
    mutationFn: () =>
      announcementsApi.create(
        { title, content, isPinned: pinned, ...(branchId ? { branchId } : {}) },
        familyId,
      ),
    onSuccess: async () => {
      setTitle('');
      setContent('');
      await cache.invalidateQueries({ queryKey: ['announcements'] });
    },
  });
  const items = query.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <section aria-label="Thông báo gia đình" className="space-y-3">
      <h2 className="text-section-title">Thông báo gia đình</h2>
      {canManage && (
        <form
          className="space-y-2 rounded-card border border-border bg-surface p-4"
          onSubmit={(e) => {
            e.preventDefault();
            create.mutate();
          }}
        >
          <label className="block text-body" htmlFor="announcement-title">
            Tiêu đề
          </label>
          <input
            id="announcement-title"
            className={inputClass}
            maxLength={255}
            required
            value={title}
            onChange={(e) => setTitle(e.target.value)}
          />
          <label className="block text-body" htmlFor="announcement-content">
            Nội dung
          </label>
          <textarea
            id="announcement-content"
            className={inputClass}
            maxLength={5000}
            required
            value={content}
            onChange={(e) => setContent(e.target.value)}
          />
          <label className="flex items-center gap-2">
            <input type="checkbox" checked={pinned} onChange={(e) => setPinned(e.target.checked)} />{' '}
            Ghim ở đầu bảng tin
          </label>
          <Button
            type="submit"
            fullWidth={false}
            loading={create.isPending}
            disabled={!title.trim() || !content.trim()}
          >
            Đăng thông báo
          </Button>
          {create.isError && (
            <p role="alert" className="text-error">
              Không đăng được thông báo.
            </p>
          )}
        </form>
      )}
      {query.isPending && <p>Đang tải thông báo...</p>}
      {query.isError && (
        <div role="alert" className="space-y-2">
          <p className="text-error">Không tải được thông báo.</p>
          <Button variant="secondary" fullWidth={false} onClick={() => void query.refetch()}>Thử lại</Button>
        </div>
      )}
      {items.map((item) => (
        <AnnouncementCard key={item.id} item={item} canManage={canManage} canReport={canReport} />
      ))}
      {!query.isPending && !query.isError && items.length === 0 && (
        <p className="text-body text-text-secondary">Chưa có thông báo gia đình.</p>
      )}
      {query.hasNextPage && (
        <Button
          variant="secondary"
          fullWidth={false}
          loading={query.isFetchingNextPage}
          onClick={() => query.fetchNextPage()}
        >
          Xem thêm thông báo
        </Button>
      )}
    </section>
  );
}
