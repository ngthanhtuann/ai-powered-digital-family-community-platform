'use client';

import { useMemo, useState } from 'react';
import {
  useInfiniteQuery, useMutation, useQuery, useQueryClient,
  type InfiniteData, type QueryKey,
} from '@tanstack/react-query';
import { communityApi, type Comment, type Post, type ReactionType } from '@/services/community/posts';

type FeedPage = Awaited<ReturnType<typeof communityApi.posts>>;
type FeedCache = InfiniteData<FeedPage, number>;

const reactions: { type: ReactionType; label: string }[] = [
  { type: 'LIKE', label: '👍 Thích' },
  { type: 'LOVE', label: '❤️ Yêu thích' },
  { type: 'HAHA', label: '😄 Vui' },
  { type: 'SAD', label: '😢 Buồn' },
];

const dateText = (value: string) => new Date(value).toLocaleString('vi-VN');
const errorText = (error: unknown) => error instanceof Error ? error.message : 'Có lỗi xảy ra.';
const inputClass = 'w-full rounded-lg border border-border bg-surface px-3 py-2 text-text-primary';
const buttonClass = 'rounded-lg bg-primary px-4 py-2 font-medium text-cream-on-dark disabled:opacity-50';

function Comments({ postId, userId, canModerate }: {
  postId: string; userId?: string; canModerate: boolean;
}) {
  const client = useQueryClient();
  const [draft, setDraft] = useState('');
  const [editing, setEditing] = useState<string | null>(null);
  const [editContent, setEditContent] = useState('');
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
  };
  const add = useMutation({
    mutationFn: () => communityApi.createComment(postId, draft),
    onSuccess: async () => { setDraft(''); await refresh(); },
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
  return (
    <section className="mt-4 border-t border-border pt-4" aria-label="Bình luận">
      <h3 className="mb-3 font-semibold">Bình luận</h3>
      {query.isPending && <p className="text-text-secondary">Đang tải bình luận...</p>}
      {query.isError && <p role="alert" className="text-error">{errorText(query.error)}</p>}
      <ul className="space-y-3">
        {comments.map((comment: Comment) => (
          <li key={comment.id} className="rounded-lg bg-surface-muted p-3">
            <p className="text-xs text-text-secondary">
              Người đăng: {comment.createdBy} · {dateText(comment.createdAt)}
            </p>
            {editing === comment.id ? (
              <form className="mt-2 space-y-2" onSubmit={(event) => { event.preventDefault(); update.mutate(); }}>
                <textarea className={inputClass} maxLength={1000} required value={editContent}
                  onChange={(event) => setEditContent(event.target.value)} />
                <div className="flex gap-2">
                  <button className={buttonClass} disabled={update.isPending || !editContent.trim()}>Lưu</button>
                  <button type="button" onClick={() => setEditing(null)}>Hủy</button>
                </div>
              </form>
            ) : (
              <p className="mt-2 whitespace-pre-wrap break-words">{comment.content}</p>
            )}
            {(comment.createdBy === userId || canModerate) && editing !== comment.id && (
              <div className="mt-2 flex gap-3 text-sm text-text-secondary">
                {comment.createdBy === userId && (
                  <button type="button" onClick={() => {
                    setEditing(comment.id); setEditContent(comment.content);
                  }}>Sửa</button>
                )}
                <button type="button" disabled={remove.isPending}
                  onClick={() => { if (window.confirm('Xóa bình luận này?')) remove.mutate(comment.id); }}>
                  Xóa
                </button>
              </div>
            )}
          </li>
        ))}
      </ul>
      {query.hasNextPage && (
        <button className="mt-3 underline" type="button" disabled={query.isFetchingNextPage}
          onClick={() => query.fetchNextPage()}>
          {query.isFetchingNextPage ? 'Đang tải...' : 'Xem thêm bình luận'}
        </button>
      )}
      {!query.isPending && comments.length === 0 && <p className="text-text-secondary">Chưa có bình luận.</p>}
      <form className="mt-4 space-y-2" onSubmit={(event) => { event.preventDefault(); add.mutate(); }}>
        <label htmlFor={'comment-' + postId} className="block font-medium">Viết bình luận</label>
        <textarea id={'comment-' + postId} className={inputClass} maxLength={1000}
          value={draft} required onChange={(event) => setDraft(event.target.value)}
          placeholder="Nhập bình luận..." />
        <div className="flex items-center justify-between">
          <span className="text-xs text-text-secondary">{draft.length}/1000</span>
          <button className={buttonClass} disabled={add.isPending || !draft.trim()}>Gửi bình luận</button>
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

function PostCard({ post, userId, canModerate, onReaction }: {
  post: Post; userId?: string; canModerate: boolean;
  onReaction: (post: Post, next: ReactionType | null) => void;
}) {
  const client = useQueryClient();
  const [showComments, setShowComments] = useState(false);
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState(post.content);
  const update = useMutation({
    mutationFn: () => communityApi.updatePost(post.id, content),
    onSuccess: async () => { setEditing(false); await client.invalidateQueries({ queryKey: ['community-posts'] }); },
  });
  const remove = useMutation({
    mutationFn: () => communityApi.deletePost(post.id),
    onSuccess: () => client.invalidateQueries({ queryKey: ['community-posts'] }),
  });
  return (
    <article className="rounded-xl border border-border bg-surface p-5 shadow-sm">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div>
          <span className="rounded-full bg-surface-muted px-3 py-1 text-xs font-medium">
            {post.type === 'NEWS' ? 'Tin tức gia đình' : 'Bài đăng'}
          </span>
          <p className="mt-2 text-xs text-text-secondary">
            Người đăng: {post.createdBy} · {dateText(post.createdAt)}
          </p>
        </div>
        {(post.createdBy === userId || canModerate) && (
          <div className="flex gap-3 text-sm">
            {post.createdBy === userId && !editing && (
              <button type="button" onClick={() => { setContent(post.content); setEditing(true); }}>Sửa</button>
            )}
            <button type="button" disabled={remove.isPending}
              onClick={() => { if (window.confirm('Xóa bài viết này?')) remove.mutate(); }}>
              Xóa
            </button>
          </div>
        )}
      </div>
      {editing ? (
        <form className="space-y-2" onSubmit={(event) => { event.preventDefault(); update.mutate(); }}>
          <textarea className={inputClass} maxLength={5000} required value={content}
            onChange={(event) => setContent(event.target.value)} />
          <div className="flex gap-2">
            <button className={buttonClass} disabled={update.isPending || !content.trim()}>Lưu bài</button>
            <button type="button" onClick={() => setEditing(false)}>Hủy</button>
          </div>
        </form>
      ) : (
        <p className="whitespace-pre-wrap break-words">{post.content}</p>
      )}
      <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Reaction">
        {reactions.map(({ type, label }) => (
          <button key={type} type="button" aria-pressed={post.myReaction === type}
            className={'rounded-full border px-3 py-1 text-sm ' +
              (post.myReaction === type ? 'border-primary bg-surface-muted font-semibold' : 'border-border')}
            onClick={() => onReaction(post, post.myReaction === type ? null : type)}>
            {label} {post.reactionCounts[type]}
          </button>
        ))}
      </div>
      <button type="button" className="mt-4 text-sm font-medium underline"
        onClick={() => setShowComments((value) => !value)}>
        {showComments ? 'Ẩn bình luận' : 'Bình luận'} ({post.commentCount})
      </button>
      {(update.isError || remove.isError) &&
        <p className="mt-2 text-error" role="alert">{errorText(update.error || remove.error)}</p>}
      {showComments && <Comments postId={post.id} userId={userId} canModerate={canModerate} />}
    </article>
  );
}

export default function CommunityPage() {
  const client = useQueryClient();
  const [tab, setTab] = useState<'ALL' | 'NEWS'>('ALL');
  const [postType, setPostType] = useState<'POST' | 'NEWS'>('POST');
  const [draft, setDraft] = useState('');
  const [adminFamily, setAdminFamily] = useState('');
  const membership = useQuery({ queryKey: ['me-membership'], queryFn: communityApi.membership });
  const me = useQuery({ queryKey: ['me-user'], queryFn: communityApi.user });
  const systemAdmin = me.data?.roles.includes('SYSTEM_ADMIN') ?? false;
  const branchAdmin = membership.data?.role === 'BRANCH_ADMIN';
  const canNews = branchAdmin || systemAdmin;
  const familyId = systemAdmin ? adminFamily.trim() : undefined;
  const ready = systemAdmin ? Boolean(familyId) : membership.data?.status === 'APPROVED';

  const feedKey = useMemo(() => ['community-posts', tab, familyId ?? 'own'], [tab, familyId]);
  const feed = useInfiniteQuery({
    queryKey: feedKey,
    enabled: ready,
    initialPageParam: 0,
    queryFn: ({ pageParam }) => communityApi.posts(pageParam, tab === 'NEWS' ? 'NEWS' : undefined, familyId),
    getNextPageParam: (last) =>
      (last.meta.page + 1) * last.meta.size < last.meta.total ? last.meta.page + 1 : undefined,
  });
  const create = useMutation({
    mutationFn: () => communityApi.createPost(draft, postType, familyId),
    onSuccess: async () => { setDraft(''); await client.invalidateQueries({ queryKey: ['community-posts'] }); },
  });
  const optimisticReaction = useMutation({
    mutationFn: ({ post, next }: { post: Post; next: ReactionType | null }) =>
      next ? communityApi.react(post.id, next) : communityApi.removeReaction(post.id),
    onMutate: async ({ post, next }) => {
      await client.cancelQueries({ queryKey: ['community-posts'] });
      const previous = client.getQueriesData<FeedCache>({ queryKey: ['community-posts'] });
      client.setQueriesData<FeedCache>({ queryKey: ['community-posts'] }, (data) => {
        if (!data) return data;
        return {
          ...data,
          pages: data.pages.map((page) => ({
            ...page,
            items: page.items.map((item) => {
              if (item.id !== post.id) return item;
              const counts = { ...item.reactionCounts };
              if (item.myReaction) counts[item.myReaction] = Math.max(0, counts[item.myReaction] - 1);
              if (next) counts[next] += 1;
              return { ...item, myReaction: next, reactionCounts: counts };
            }),
          })),
        };
      });
      return { previous };
    },
    onError: (_error, _vars, context) => {
      context?.previous.forEach(([key, data]: [QueryKey, FeedCache | undefined]) =>
        client.setQueryData(key, data));
    },
    onSettled: () => client.invalidateQueries({ queryKey: ['community-posts'] }),
  });

  const posts = feed.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <main className="mx-auto max-w-3xl space-y-6 px-4 py-8">
      <header>
        <h1 className="text-page-title">Bảng tin gia đình</h1>
        <p className="mt-1 text-text-secondary">Bài đăng và tin tức trong phạm vi gia đình của bạn.</p>
      </header>
      {(membership.isPending || me.isPending) && <p>Đang kiểm tra quyền thành viên...</p>}
      {(membership.isError || me.isError) &&
        <p role="alert" className="rounded-lg bg-surface p-4 text-error">
          Chưa thể xác minh phiên đăng nhập: {errorText(membership.error || me.error)}
        </p>}
      {!systemAdmin && membership.data && membership.data.status !== 'APPROVED' &&
        <p className="rounded-lg bg-surface p-4">Bạn cần được xác minh thành viên trước khi xem bảng tin.</p>}
      {systemAdmin && (
        <div className="rounded-lg border border-border bg-surface p-4">
          <label htmlFor="family-id" className="mb-2 block font-medium">Mã gia đình cần quản lý</label>
          <input id="family-id" className={inputClass} value={adminFamily}
            onChange={(event) => setAdminFamily(event.target.value)} placeholder="UUID gia đình" />
        </div>
      )}
      {ready && (
        <>
          <nav aria-label="Lọc bài đăng" className="flex border-b border-border">
            {([{ key: 'ALL', label: 'Tất cả' }, { key: 'NEWS', label: 'Tin tức gia đình' }] as const)
              .map(({ key, label }) => (
                <button key={key} type="button" aria-current={tab === key ? 'page' : undefined}
                  className={'border-b-2 px-4 py-3 font-medium ' +
                    (tab === key ? 'border-primary text-primary' : 'border-transparent text-text-secondary')}
                  onClick={() => setTab(key)}>{label}</button>
              ))}
          </nav>
          <form className="space-y-3 rounded-xl border border-border bg-surface p-5"
            onSubmit={(event) => { event.preventDefault(); create.mutate(); }}>
            <h2 className="font-semibold">Tạo bài đăng</h2>
            <label htmlFor="post-kind" className="block text-sm">Loại bài</label>
            <select id="post-kind" className={inputClass} value={postType}
              onChange={(event) => setPostType(event.target.value as 'POST' | 'NEWS')}>
              <option value="POST">Bài đăng thường</option>
              {canNews && <option value="NEWS">Tin tức gia đình</option>}
            </select>
            <label htmlFor="post-content" className="block text-sm">Nội dung</label>
            <textarea id="post-content" className={inputClass} rows={4} maxLength={5000} required
              placeholder="Chia sẻ cùng gia đình..." value={draft}
              onChange={(event) => setDraft(event.target.value)} />
            <div className="flex items-center justify-between">
              <span className="text-xs text-text-secondary">{draft.length}/5000 ký tự</span>
              <button className={buttonClass} disabled={create.isPending || !draft.trim()}>
                {create.isPending ? 'Đang đăng...' : 'Đăng bài'}
              </button>
            </div>
            {create.isError && <p role="alert" className="text-error">{errorText(create.error)}</p>}
          </form>
          <section className="space-y-4" aria-label="Danh sách bài đăng">
            {feed.isPending && <p>Đang tải bảng tin...</p>}
            {feed.isError && <p role="alert" className="text-error">{errorText(feed.error)}</p>}
            {!feed.isPending && !feed.isError && posts.length === 0 &&
              <p className="rounded-lg bg-surface p-6 text-text-secondary">Chưa có bài đăng.</p>}
            {posts.map((post) => (
              <PostCard key={post.id} post={post} userId={me.data?.id}
                canModerate={canNews}
                onReaction={(target, next) => optimisticReaction.mutate({ post: target, next })} />
            ))}
            {optimisticReaction.isError &&
              <p role="alert" className="text-error">Không thể cập nhật reaction: {errorText(optimisticReaction.error)}</p>}
            {feed.hasNextPage && (
              <button type="button" className={buttonClass} disabled={feed.isFetchingNextPage}
                onClick={() => feed.fetchNextPage()}>
                {feed.isFetchingNextPage ? 'Đang tải...' : 'Xem thêm'}
              </button>
            )}
          </section>
        </>
      )}
    </main>
  );
}
