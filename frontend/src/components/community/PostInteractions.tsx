'use client';

import { useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { communityApi, type Comment, type Post, type ReactionType } from '@/services/community/posts';
import { formatDateTime } from '@/lib/datetime';

// Bình luận, báo cáo vi phạm và cảm xúc dùng chung cho Bảng tin và trang cá nhân (SCRUM-120).

export const reactions: { type: ReactionType; label: string }[] = [
  { type: 'LIKE', label: '👍 Thích' },
  { type: 'LOVE', label: '❤️ Yêu thích' },
  { type: 'HAHA', label: '😄 Vui' },
  { type: 'SAD', label: '😢 Buồn' },
];

export const dateText = (value: string) => formatDateTime(value);
export const errorText = (error: unknown) => error instanceof Error ? error.message : 'Có lỗi xảy ra.';
// Cùng kiểu ô nhập với TextField và StoryForm (design-system.md mục 4).
export const inputClass = 'w-full rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';
// Nút dạng chữ vẫn đủ vùng chạm 44px.
export const linkButtonClass = 'min-h-11 px-1 text-sm font-medium underline-offset-2 hover:underline disabled:opacity-60';

export type ReportTarget = { targetType: 'POST' | 'COMMENT'; targetId: string; label: string };

/** BR-COM-12: thành viên báo cáo nội dung vi phạm; quản trị hệ thống xử lý ở /admin. */
export function ReportButton({ target }: { target: ReportTarget }) {
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const report = useMutation({
    mutationFn: () => communityApi.report(target.targetType, target.targetId, reason.trim()),
  });
  const close = () => { setOpen(false); setReason(''); report.reset(); };
  return (
    <>
      <button type="button" className={linkButtonClass + ' text-text-secondary'} onClick={() => setOpen(true)}>
        Báo cáo
      </button>
      {open && (
        <Modal open title={`Báo cáo ${target.label}`} onClose={close}
          description="Quản trị hệ thống sẽ xem xét và ẩn nội dung nếu vi phạm.">
          {report.isSuccess ? (
            <div className="space-y-4">
              <p role="status" className="text-body">Đã gửi báo cáo. Cảm ơn bạn.</p>
              <Button onClick={close}>Đóng</Button>
            </div>
          ) : (
            <form className="space-y-3" onSubmit={(event) => { event.preventDefault(); report.mutate(); }}>
              <label htmlFor={'report-' + target.targetId} className="block font-medium">Lý do</label>
              <textarea id={'report-' + target.targetId} className={inputClass} maxLength={255} required
                value={reason} onChange={(event) => setReason(event.target.value)}
                placeholder="Ví dụ: quảng cáo, nội dung xúc phạm..." />
              <p className="text-xs text-text-secondary">{reason.length}/255</p>
              {report.isError && <p role="alert" className="text-error">{errorText(report.error)}</p>}
              <Button type="submit" loading={report.isPending} disabled={!reason.trim()}>Gửi báo cáo</Button>
            </form>
          )}
        </Modal>
      )}
    </>
  );
}

export function Comments({ postId, userId, canReport }: { postId: string; userId?: string; canReport: boolean }) {
  const client = useQueryClient();
  const [draft, setDraft] = useState('');
  const [editing, setEditing] = useState<string | null>(null);
  const [editContent, setEditContent] = useState('');
  const [replyTo, setReplyTo] = useState<Comment | null>(null);
  const [replyDraft, setReplyDraft] = useState('');
  const query = useInfiniteQuery({
    queryKey: ['community-comments', postId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => communityApi.comments(postId, pageParam),
    getNextPageParam: (last) =>
      (last.meta.page + 1) * last.meta.size < last.meta.total ? last.meta.page + 1 : undefined,
  });
  const refresh = async () => {
    await client.invalidateQueries({ queryKey: ['community-comments', postId] });
    await client.invalidateQueries({ queryKey: ['community-posts'] });
    await client.invalidateQueries({ queryKey: ['person-posts'] }); // số bình luận trên trang cá nhân
  };
  const add = useMutation({
    mutationFn: () => communityApi.createComment(postId, draft),
    onSuccess: async () => { setDraft(''); await refresh(); },
  });
  const reply = useMutation({
    mutationFn: () => communityApi.createComment(postId, replyDraft, replyTo!.id),
    onSuccess: async () => { setReplyTo(null); setReplyDraft(''); await refresh(); },
  });
  const update = useMutation({
    mutationFn: () => communityApi.updateComment(editing!, editContent),
    onSuccess: async () => { setEditing(null); await refresh(); },
  });
  const remove = useMutation({
    mutationFn: (id: string) => communityApi.deleteComment(id),
    onSuccess: refresh,
  });
  const comments = query.data?.pages.flatMap((page) => page.items) ?? [];
  const ids = new Set(comments.map((c) => c.id));
  // Câu trả lời mà bình luận gốc chưa tải (trang sau) thì hiện như bình luận thường.
  const roots = comments.filter((c) => !c.parentId || !ids.has(c.parentId));
  const repliesOf = new Map<string, Comment[]>();
  for (const c of comments) {
    if (c.parentId && ids.has(c.parentId)) repliesOf.set(c.parentId, [...(repliesOf.get(c.parentId) ?? []), c]);
  }
  // Bảng tin xếp mới nhất trước; câu trả lời đọc theo thứ tự thời gian.
  repliesOf.forEach((list) => list.sort((a, b) => a.createdAt.localeCompare(b.createdAt)));
  // Trả lời 1 cấp (BR-COM-14): câu trả lời hiện thụt vào dưới bình luận gốc.
  const renderComment = (comment: Comment) => (
          <li key={comment.id} className="rounded-lg bg-surface-muted p-3">
            <p className="text-xs text-text-secondary">
              {comment.authorName} · {dateText(comment.createdAt)}
            </p>
            {editing === comment.id ? (
              <form className="mt-2 space-y-2" onSubmit={(event) => { event.preventDefault(); update.mutate(); }}>
                <textarea className={inputClass} maxLength={1000} required value={editContent}
                  onChange={(event) => setEditContent(event.target.value)} />
                <div className="flex gap-2">
                  <Button type="submit" fullWidth={false} loading={update.isPending} disabled={!editContent.trim()}>Lưu</Button>
                  <Button variant="secondary" fullWidth={false} onClick={() => setEditing(null)}>Hủy</Button>
                </div>
              </form>
            ) : (
              <p className="mt-2 whitespace-pre-wrap break-words">{comment.content}</p>
            )}
            {(comment.createdBy === userId || comment.canDelete || canReport) && editing !== comment.id && (
              <div className="mt-2 flex gap-3 text-sm text-text-secondary">
                {canReport && comment.createdBy !== userId && (
                  <ReportButton target={{ targetType: 'COMMENT', targetId: comment.id, label: 'bình luận' }} />
                )}
                {comment.createdBy === userId && (
                  <button type="button" className={linkButtonClass} onClick={() => {
                    setEditing(comment.id); setEditContent(comment.content);
                  }}>Sửa</button>
                )}
                {comment.canDelete && (
                  <button type="button" className={linkButtonClass + ' text-error'} disabled={remove.isPending}
                    onClick={() => { if (window.confirm('Xóa bình luận này?')) remove.mutate(comment.id); }}>
                    Xóa
                  </button>
                )}
              </div>
            )}
            {editing !== comment.id && (
              <button type="button" className={linkButtonClass + ' text-info'}
                onClick={() => { setReplyTo(comment); setReplyDraft(''); }}>
                Trả lời
              </button>
            )}
            {(repliesOf.get(comment.id) ?? []).length > 0 && (
              <ul className="ml-4 mt-3 space-y-2 border-l-2 border-border pl-3" aria-label="Các câu trả lời">
                {(repliesOf.get(comment.id) ?? []).map(renderComment)}
              </ul>
            )}
            {replyTo && (replyTo.id === comment.id || replyTo.parentId === comment.id) && (
              <form className="ml-4 mt-3 space-y-2" onSubmit={(event) => { event.preventDefault(); reply.mutate(); }}>
                <label htmlFor={'reply-' + comment.id} className="block text-sm font-medium">
                  Trả lời {replyTo.authorName}
                </label>
                <textarea id={'reply-' + comment.id} className={inputClass} maxLength={1000} required autoFocus
                  value={replyDraft} onChange={(event) => setReplyDraft(event.target.value)} />
                <div className="flex gap-2">
                  <Button type="submit" fullWidth={false} loading={reply.isPending} disabled={!replyDraft.trim()}>Gửi trả lời</Button>
                  <Button variant="secondary" fullWidth={false} onClick={() => setReplyTo(null)}>Hủy</Button>
                </div>
                {reply.isError && <p role="alert" className="text-error">{errorText(reply.error)}</p>}
              </form>
            )}
          </li>
  );
  return (
    <section className="mt-4 border-t border-border pt-4" aria-label="Bình luận">
      <h3 className="mb-3 font-semibold">Bình luận</h3>
      {query.isPending && <p className="text-text-secondary">Đang tải bình luận...</p>}
      {query.isError && (
        <div role="alert" className="space-y-2">
          <p className="break-words text-error">{errorText(query.error)}</p>
          <Button variant="secondary" fullWidth={false} onClick={() => void query.refetch()}>Thử lại</Button>
        </div>
      )}
      <ul className="space-y-3">
        {roots.map(renderComment)}
      </ul>
      {query.hasNextPage && (
        <button className={'mt-3 ' + linkButtonClass} type="button" disabled={query.isFetchingNextPage}
          onClick={() => query.fetchNextPage()}>
          {query.isFetchingNextPage ? 'Đang tải...' : 'Xem thêm bình luận'}
        </button>
      )}
      {!query.isPending && !query.isError && comments.length === 0 &&
        <p className="text-text-secondary">Chưa có bình luận.</p>}
      <form className="mt-4 space-y-2" onSubmit={(event) => { event.preventDefault(); add.mutate(); }}>
        <label htmlFor={'comment-' + postId} className="block font-medium">Viết bình luận</label>
        <textarea id={'comment-' + postId} className={inputClass} maxLength={1000}
          value={draft} required onChange={(event) => setDraft(event.target.value)}
          placeholder="Nhập bình luận..." />
        <div className="flex items-center justify-between">
          <span className="text-xs text-text-secondary">{draft.length}/1000</span>
          <Button type="submit" fullWidth={false} loading={add.isPending} disabled={!draft.trim()}>Gửi bình luận</Button>
        </div>
        {(add.isError || update.isError || remove.isError) && (
          <p role="alert" className="text-error">
            {errorText(add.error || update.error || remove.error)}
          </p>
        )}
      </form>
    </section>
  );
}


/** Nút cảm xúc cho nơi không giữ cache bảng tin (trang cá nhân): bấm xong tải lại các query trong invalidateKey. */
export function ReactionBar({ post, invalidateKey }: { post: Post; invalidateKey: readonly unknown[] }) {
  const client = useQueryClient();
  const react = useMutation({
    mutationFn: (next: ReactionType | null) =>
      next ? communityApi.react(post.id, next) : communityApi.removeReaction(post.id),
    onSettled: () => client.invalidateQueries({ queryKey: invalidateKey }),
  });
  return (
    <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Cảm xúc">
      {reactions.map(({ type, label }) => (
        <button key={type} type="button" aria-pressed={post.myReaction === type} disabled={react.isPending}
          className={'min-h-11 rounded-full border px-3 text-sm ' +
            (post.myReaction === type ? 'border-primary bg-surface-muted font-semibold' : 'border-border')}
          onClick={() => react.mutate(post.myReaction === type ? null : type)}>
          {label} {post.reactionCounts[type]}
        </button>
      ))}
      {react.isError && <p role="alert" className="w-full text-error">{errorText(react.error)}</p>}
    </div>
  );
}
