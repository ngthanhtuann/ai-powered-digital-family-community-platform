'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  useInfiniteQuery, useMutation, useQuery, useQueryClient,
  type InfiniteData, type QueryKey,
} from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { dashboardApi } from '@/services/dashboard';
import { ImagePicker } from '@/components/community/ImagePicker';
import { ProtectedPostImages } from '@/components/community/ProtectedPostImages';
import { Announcements } from '@/components/community/Announcements';
import { communityApi, type Comment, type Post, type ReactionType } from '@/services/community/posts';
import { branchesOf } from '@/services/admin';
import { AiSummaryPanel } from '@/components/ai/AiSummaryPanel';

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
// Cùng kiểu ô nhập với TextField và StoryForm (design-system.md mục 4).
const inputClass = 'w-full rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';
// Nút dạng chữ vẫn đủ vùng chạm 44px.
const linkButtonClass = 'min-h-11 px-1 text-sm font-medium underline-offset-2 hover:underline disabled:opacity-60';

type ReportTarget = { targetType: 'POST' | 'COMMENT'; targetId: string; label: string };
type BranchNode = { id?: string; name?: string; parentBranchId?: string | null };

/** branch là ancestor hoặc nằm dưới ancestor (đi theo parentBranchId). */
function within(parents: Map<string, string | null>, branch: string, ancestor: string) {
  let current: string | null | undefined = branch;
  for (let step = 0; current && step <= parents.size; step++) {
    if (current === ancestor) return true;
    current = parents.get(current);
  }
  return false;
}

const scopeHint = (branchId: string, label: string) => branchId
  ? `Chỉ người trong ${label} và các chi con thấy, cùng trưởng chi, trưởng họ quản lý.`
  : 'Mọi thành viên trong dòng họ đều thấy.';

/** Chi được chọn khi đăng bài: cùng quy tắc đọc với backend (BR-COM-03, ModulePlatformAdapter.canRead). */
function postableBranches(branches: BranchNode[], wholeFamily: boolean, myBranch: string | null, branchAdmin: boolean) {
  if (wholeFamily) return branches;
  if (!myBranch) return [];
  const parents = new Map(branches.map((b) => [b.id ?? '', b.parentBranchId ?? null]));
  return branches.filter((b) => b.id
    && (within(parents, myBranch, b.id) || (branchAdmin && within(parents, b.id, myBranch))));
}

/** BR-COM-12: thành viên báo cáo nội dung vi phạm; quản trị hệ thống xử lý ở /admin. */
function ReportButton({ target }: { target: ReportTarget }) {
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

function Comments({ postId, userId, canReport }: { postId: string; userId?: string; canReport: boolean }) {
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
      {query.isError && (
        <div role="alert" className="space-y-2">
          <p className="break-words text-error">{errorText(query.error)}</p>
          <Button variant="secondary" fullWidth={false} onClick={() => void query.refetch()}>Thử lại</Button>
        </div>
      )}
      <ul className="space-y-3">
        {comments.map((comment: Comment) => (
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
          </li>
        ))}
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

function PostCard({ post, userId, canReport, scopeOptions, labelOf, onReaction }: {
  post: Post; userId?: string; canReport: boolean;
  scopeOptions: BranchNode[]; labelOf: (branchId?: string | null) => string;
  onReaction: (post: Post, next: ReactionType | null) => void;
}) {
  const client = useQueryClient();
  const [showComments, setShowComments] = useState(false);
  const [editing, setEditing] = useState(false);
  const [content, setContent] = useState(post.content);
  const currentScope = post.branchId ?? '';
  const [editScope, setEditScope] = useState(currentScope);
  // Giữ phạm vi hiện tại trong danh sách dù người đăng đã chuyển chi, để sửa nội dung không vô tình đổi phạm vi.
  const editOptions = scopeOptions.some((b) => b.id === currentScope) || !currentScope
    ? scopeOptions : [...scopeOptions, { id: currentScope, name: labelOf(currentScope) }];
  const update = useMutation({
    mutationFn: () => communityApi.updatePost(post.id, content, editScope || null),
    onSuccess: async () => { setEditing(false); await client.invalidateQueries({ queryKey: ['community-posts'] }); },
  });
  const remove = useMutation({
    mutationFn: () => communityApi.deletePost(post.id),
    onSuccess: () => client.invalidateQueries({ queryKey: ['community-posts'] }),
  });
  return (
    <article id={`post-${post.id}`} className="min-w-0 rounded-xl border border-border bg-surface p-4 shadow-sm sm:p-5">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <div className="min-w-0 max-w-full">
          <div className="flex flex-wrap gap-2">
            <span className="rounded-full bg-surface-muted px-3 py-1 text-xs font-medium">
              {post.type === 'NEWS' ? 'Tin tức gia đình' : 'Bài đăng'}
            </span>
            <span className="max-w-full break-words rounded-full border border-border px-3 py-1 text-xs font-medium text-text-secondary"
              title="Phạm vi người được xem bài">
              {labelOf(post.branchId)}
            </span>
          </div>
          <p className="mt-2 break-words text-xs text-text-secondary">
            {post.authorName} · {dateText(post.createdAt)}
          </p>
        </div>
        {(post.createdBy === userId || post.canDelete || canReport) && (
          <div className="flex flex-wrap gap-2">
            {canReport && post.createdBy !== userId && (
              <ReportButton target={{ targetType: 'POST', targetId: post.id, label: 'bài viết' }} />
            )}
            {post.createdBy === userId && !editing && (
              <Button variant="secondary" fullWidth={false}
                onClick={() => { setContent(post.content); setEditScope(currentScope); setEditing(true); }}>Sửa</Button>
            )}
            {post.canDelete && (
              <Button variant="danger" fullWidth={false} loading={remove.isPending}
                onClick={() => { if (window.confirm('Xóa bài viết này?')) remove.mutate(); }}>
                Xóa
              </Button>
            )}
          </div>
        )}
      </div>
      {editing ? (
        <form className="space-y-2" onSubmit={(event) => { event.preventDefault(); update.mutate(); }}>
          <textarea className={inputClass} maxLength={5000} required value={content}
            onChange={(event) => setContent(event.target.value)} />
          <label htmlFor={'scope-' + post.id} className="block text-sm">Ai được xem</label>
          <select id={'scope-' + post.id} className={inputClass + ' h-[42px] py-0'} value={editScope}
            aria-describedby={'scope-help-' + post.id} onChange={(event) => setEditScope(event.target.value)}>
            <option value="">Cả dòng họ</option>
            {editOptions.map((b) => <option key={b.id} value={b.id ?? ''}>{b.name}</option>)}
          </select>
          {editScope !== currentScope && (
            <p id={'scope-help-' + post.id} className="text-xs text-text-secondary">
              Sau khi lưu: {scopeHint(editScope, labelOf(editScope))}
            </p>
          )}
          <div className="flex gap-2">
            <Button type="submit" fullWidth={false} loading={update.isPending} disabled={!content.trim()}>Lưu bài</Button>
            <Button variant="secondary" fullWidth={false} onClick={() => setEditing(false)}>Hủy</Button>
          </div>
        </form>
      ) : (
        <p className="whitespace-pre-wrap break-words">{post.content}</p>
      )}
      {!editing && <AiSummaryPanel sourceType="POST" sourceId={post.id} />}
      <ProtectedPostImages postId={post.id} preview={post.media ?? []} total={post.mediaCount ?? 0} />
      <div className="mt-4 flex flex-wrap gap-2" role="group" aria-label="Reaction">
        {reactions.map(({ type, label }) => (
          <button key={type} type="button" aria-pressed={post.myReaction === type}
            className={'min-h-11 rounded-full border px-3 text-sm ' +
              (post.myReaction === type ? 'border-primary bg-surface-muted font-semibold' : 'border-border')}
            onClick={() => onReaction(post, post.myReaction === type ? null : type)}>
            {label} {post.reactionCounts[type]}
          </button>
        ))}
      </div>
      <button type="button" className={'mt-2 ' + linkButtonClass}
        onClick={() => setShowComments((value) => !value)}>
        {showComments ? 'Ẩn bình luận' : 'Bình luận'} ({post.commentCount})
      </button>
      {(update.isError || remove.isError) &&
        <p className="mt-2 text-error" role="alert">{errorText(update.error || remove.error)}</p>}
      {showComments && <Comments postId={post.id} userId={userId} canReport={canReport} />}
    </article>
  );
}

export default function CommunityPage() {
  const [targetPostId, setTargetPostId] = useState<string | null>(null);
  const client = useQueryClient();
  const [tab, setTab] = useState<'ALL' | 'NEWS'>('ALL');
  const [postType, setPostType] = useState<'POST' | 'NEWS'>('POST');
  const [draft, setDraft] = useState('');
  const [selectedImages, setSelectedImages] = useState<File[]>([]);
  const [uploadProgress, setUploadProgress] = useState<number>();
  const [pendingPostId, setPendingPostId] = useState<string | null>(null);
  const [uploadedCount, setUploadedCount] = useState(0);
  const [adminFamily, setAdminFamily] = useState('');
  const membership = useQuery({ queryKey: ['me-membership'], queryFn: communityApi.membership });
  const me = useQuery({ queryKey: ['me-user'], queryFn: communityApi.user });
  const systemAdmin = me.data?.roles.includes('SYSTEM_ADMIN') ?? false;
  const branchAdmin = membership.data?.role === 'BRANCH_ADMIN';
  const canNews = branchAdmin || systemAdmin;
  // SYSTEM_ADMIN chọn gia đình từ GET /families (trả mọi gia đình cho quản trị), như trang Dashboard.
  const families = useQuery({ queryKey: ['dashboard-families'], queryFn: dashboardApi.families, enabled: systemAdmin });
  const familyId = systemAdmin ? adminFamily || families.data?.[0]?.id || '' : undefined;
  const ready = systemAdmin ? Boolean(familyId) : membership.data?.status === 'APPROVED';
  const myBranch = membership.data?.branchId ?? null;
  const branchFamily = (systemAdmin ? familyId : membership.data?.familyId) ?? '';
  const branches = useQuery({
    queryKey: ['branches', branchFamily],
    enabled: ready && /^[0-9a-f-]{36}$/i.test(branchFamily),
    queryFn: () => branchesOf(branchFamily),
  });
  const branchName = useMemo(
    () => new Map((branches.data?.items ?? []).map((b) => [b.id ?? '', b.name ?? ''])), [branches.data]);
  const scopeOptions = useMemo(() => postableBranches(branches.data?.items ?? [],
    systemAdmin || (branchAdmin && !myBranch), myBranch, branchAdmin), [branches.data, systemAdmin, branchAdmin, myBranch]);
  // Mặc định là chi của mình; muốn cả họ thấy thì người đăng chọn "Cả dòng họ". '' là cả dòng họ.
  const [scopeChoice, setScopeChoice] = useState<string>();
  const wanted = scopeChoice ?? myBranch ?? '';
  const scope = scopeOptions.some((b) => b.id === wanted) ? wanted : '';
  const scopeLabel = (branchId?: string | null) => branchId ? branchName.get(branchId) ?? 'Một chi' : 'Cả dòng họ';

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
    mutationFn: async () => {
      // Mỗi request /posts/{id}/media nhận 1 file, theo OpenAPI. Nhiều ảnh tải lần lượt.
      let postId = pendingPostId;
      if (!postId) {
        postId = (await communityApi.createPost(draft, postType, scope || null, familyId)).id;
        setPendingPostId(postId);
      }
      for (let i = uploadedCount; i < selectedImages.length; i++) {
        await communityApi.uploadPostMedia(postId, selectedImages[i], (percent) =>
          setUploadProgress(Math.round((i + percent / 100) / selectedImages.length * 100)));
        setUploadedCount(i + 1);
      }
      return postId;
    },
    onSuccess: async (postId) => {
      setDraft('');
      setSelectedImages([]);
      setUploadProgress(undefined);
      setPendingPostId(null);
      setUploadedCount(0);
      setScopeChoice(undefined);
      await Promise.all([
        client.invalidateQueries({ queryKey: ['community-posts'] }),
        client.invalidateQueries({ queryKey: ['post-media', postId] }),
      ]);
    },
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

  const posts = useMemo(() => feed.data?.pages.flatMap((page) => page.items) ?? [], [feed.data]);
  useEffect(() => {
    setTargetPostId(new URLSearchParams(window.location.search).get('item'));
  }, []);
  useEffect(() => {
    if (targetPostId && posts.some((post) => post.id === targetPostId)) {
      document.getElementById(`post-${targetPostId}`)?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
  }, [targetPostId, posts]);
  return (
    <main className="mx-auto max-w-3xl min-w-0 space-y-6 px-3 py-6 sm:px-4 sm:py-8">
      <header>
        <h1 className="text-page-title">Bảng tin gia đình</h1>
        <p className="mt-1 text-text-secondary">Bài đăng và tin tức trong phạm vi gia đình của bạn.</p>
      </header>
      {(membership.isPending || me.isPending) && <p>Đang kiểm tra quyền thành viên...</p>}
      {(membership.isError || me.isError) && (
        <div role="alert" className="space-y-2 rounded-lg bg-surface p-4">
          <p className="break-words text-error">Chưa thể xác minh phiên đăng nhập: {errorText(membership.error || me.error)}</p>
          <Button variant="secondary" fullWidth={false} onClick={() => {
            void membership.refetch();
            void me.refetch();
          }}>Thử lại</Button>
        </div>
      )}
      {!systemAdmin && membership.data && membership.data.status !== 'APPROVED' &&
        <p className="rounded-lg bg-surface p-4">Bạn cần được xác minh thành viên trước khi xem bảng tin.</p>}
      {systemAdmin && (
        <div className="rounded-lg border border-border bg-surface p-4">
          <label htmlFor="family-id" className="block text-sm">Gia đình cần quản lý</label>
          <select id="family-id" className={inputClass + ' mt-1 h-[42px] py-0'} value={familyId}
            disabled={families.isPending || !families.data?.length}
            onChange={(event) => { setAdminFamily(event.target.value); setScopeChoice(undefined); }}>
            {(families.data ?? []).map((family) => (
              <option key={family.id} value={family.id}>{family.name}</option>
            ))}
          </select>
          {families.isPending && <p className="mt-1 text-xs text-text-secondary">Đang tải danh sách gia đình...</p>}
          {families.isError && (
            <div role="alert" className="mt-2 space-y-2">
              <p className="break-words text-error">{errorText(families.error)}</p>
              <Button variant="secondary" fullWidth={false} onClick={() => void families.refetch()}>Thử lại</Button>
            </div>
          )}
          {families.data?.length === 0 && <p className="mt-1 text-text-secondary">Chưa có gia đình nào.</p>}
        </div>
      )}
      {ready && (
        <>
          <Announcements familyId={familyId} branchId={branchAdmin ? membership.data?.branchId ?? undefined : undefined}
            canManage={canNews} canReport={!systemAdmin} />
          <nav aria-label="Lọc bài đăng" className="flex flex-wrap border-b border-border">
            {([{ key: 'ALL', label: 'Tất cả' }, { key: 'NEWS', label: 'Tin tức gia đình' }] as const)
              .map(({ key, label }) => (
                <button key={key} type="button" aria-current={tab === key ? 'page' : undefined}
                  className={'border-b-2 px-4 py-3 font-medium ' +
                    (tab === key ? 'border-primary text-primary' : 'border-transparent text-text-secondary')}
                  onClick={() => setTab(key)}>{label}</button>
              ))}
          </nav>
          <form className="space-y-3 rounded-xl border border-border bg-surface p-4 sm:p-5"
            onSubmit={(event) => { event.preventDefault(); create.mutate(); }}>
            <h2 className="font-semibold">Tạo bài đăng</h2>
            <label htmlFor="post-kind" className="block text-sm">Loại bài</label>
            <select id="post-kind" className={inputClass + ' h-[42px] py-0'} value={postType}
              onChange={(event) => setPostType(event.target.value as 'POST' | 'NEWS')}>
              <option value="POST">Bài đăng thường</option>
              {canNews && <option value="NEWS">Tin tức gia đình</option>}
            </select>
            <label htmlFor="post-scope" className="block text-sm">Ai được xem</label>
            <select id="post-scope" className={inputClass + ' h-[42px] py-0'} value={scope}
              aria-describedby="post-scope-help"
              disabled={branches.isPending || Boolean(pendingPostId) || create.isPending}
              onChange={(event) => setScopeChoice(event.target.value)}>
              <option value="">Cả dòng họ</option>
              {scopeOptions.map((b) => (
                <option key={b.id} value={b.id ?? ''}>{b.name}{b.id === myBranch ? ' (chi của tôi)' : ''}</option>
              ))}
            </select>
            <p id="post-scope-help" className="text-xs text-text-secondary">
              {branches.isPending ? 'Đang tải danh sách chi...'
                : branches.isError ? 'Không tải được danh sách chi. Vui lòng thử lại trước khi đăng bài.'
                  : scopeHint(scope, scopeLabel(scope))}
            </p>
            {branches.isError && (
              <div role="alert">
                <Button variant="secondary" fullWidth={false} onClick={() => void branches.refetch()}>Thử tải danh sách chi</Button>
              </div>
            )}
            <label htmlFor="post-content" className="block text-sm">Nội dung</label>
            <textarea id="post-content" className={inputClass} rows={4} maxLength={5000} required
              placeholder="Chia sẻ cùng gia đình..." value={draft}
              disabled={Boolean(pendingPostId) || create.isPending}
              onChange={(event) => setDraft(event.target.value)} />
            <ImagePicker files={selectedImages} onChange={setSelectedImages}
              disabled={Boolean(pendingPostId) || create.isPending}
              progress={create.isPending && selectedImages.length > 0 ? uploadProgress : undefined} />
            <div className="flex flex-wrap items-center justify-between gap-3">
              <span className="text-xs text-text-secondary">{draft.length}/5000 ký tự</span>
              <Button type="submit" fullWidth={false} loading={create.isPending}
                disabled={(!draft.trim() && !pendingPostId) || branches.isPending || (branches.isError && !pendingPostId)}>
                {create.isPending ? 'Đang đăng...' : pendingPostId ? 'Tải tiếp ảnh' : 'Đăng bài'}
              </Button>
            </div>
            {create.isError && <p role="alert" className="text-error">{errorText(create.error)}</p>}
            {pendingPostId && create.isError && (
              <div className="space-y-2 text-sm">
                <p>Bài đã được tạo. Có thể tải tiếp từ ảnh chưa hoàn thành hoặc bỏ qua các ảnh còn lại.</p>
                <button type="button" className="underline" onClick={() => {
                  setDraft(''); setSelectedImages([]); setPendingPostId(null);
                  setUploadedCount(0); setUploadProgress(undefined); create.reset();
                  client.invalidateQueries({ queryKey: ['community-posts'] });
                }}>Bỏ qua ảnh còn lại</button>
              </div>
            )}
          </form>
          <section className="space-y-4" aria-label="Danh sách bài đăng">
            {feed.isPending && <p>Đang tải bảng tin...</p>}
            {feed.isError && (
              <div role="alert" className="space-y-2">
                <p className="break-words text-error">{errorText(feed.error)}</p>
                <Button variant="secondary" fullWidth={false} onClick={() => void feed.refetch()}>Thử lại</Button>
              </div>
            )}
            {!feed.isPending && !feed.isError && posts.length === 0 &&
              <p className="rounded-lg bg-surface p-6 text-text-secondary">Chưa có bài đăng.</p>}
            {posts.map((post) => (
              <PostCard key={post.id} post={post} userId={me.data?.id} canReport={!systemAdmin}
                scopeOptions={scopeOptions} labelOf={scopeLabel}
                onReaction={(target, next) => optimisticReaction.mutate({ post: target, next })} />
            ))}
            {optimisticReaction.isError &&
              <p role="alert" className="text-error">Không thể cập nhật reaction: {errorText(optimisticReaction.error)}</p>}
            {feed.hasNextPage && (
              <Button variant="secondary" loading={feed.isFetchingNextPage} onClick={() => feed.fetchNextPage()}>
                {feed.isFetchingNextPage ? 'Đang tải...' : 'Xem thêm'}
              </Button>
            )}
          </section>
        </>
      )}
    </main>
  );
}
