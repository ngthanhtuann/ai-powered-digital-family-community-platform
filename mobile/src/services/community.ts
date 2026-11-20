import { api } from './api';
import type { components } from './generated/community-events';
import type { components as Genealogy } from './generated/genealogy';

type Schemas = components['schemas'];
export type Post = Schemas['Post'];
export type Comment = Schemas['Comment'];
export type Media = Schemas['Media'];
export type ReactionType = Schemas['ReactionRequest']['type'];
export type UploadAsset = { uri: string; name: string; mimeType: string };
export type Branch = Genealogy['schemas']['Branch'];

/** Nhánh có phải ancestor hoặc nằm dưới ancestor không (đi theo parentBranchId, có chặn vòng lặp). */
function within(parents: Map<string, string | null>, branch: string, ancestor: string) {
  let current: string | null | undefined = branch;
  for (let step = 0; current && step <= parents.size; step++) {
    if (current === ancestor) return true;
    current = parents.get(current);
  }
  return false;
}

/**
 * Chi được chọn khi đăng bài, giống hàm postableBranches của web (SCRUM-101, BR-COM-03): chi của mình và các chi
 * cha; trưởng chi thêm các chi con mình quản lý; trưởng họ và quản trị có mọi chi.
 */
export function postableBranches(
  branches: Branch[],
  wholeFamily: boolean,
  myBranch: string | null,
  branchAdmin: boolean,
) {
  if (wholeFamily) return branches;
  if (!myBranch) return [];
  const parents = new Map(branches.map((b) => [b.id ?? '', b.parentBranchId ?? null]));
  return branches.filter(
    (b) =>
      b.id && (within(parents, myBranch, b.id) || (branchAdmin && within(parents, b.id, myBranch))),
  );
}

/** Nhãn phạm vi của bài: "Cả dòng họ" hoặc tên chi. */
/** Người xem của bài (BR-COM-13): không hiện tên chi, chỉ 3 loại như web (SCRUM-124). */
export function scopeLabel(
  branchId: string | null | undefined,
  names: Map<string, string>,
  audience?: string | null,
) {
  if (audience === 'DESCENDANTS') return 'Con cháu của người đăng';
  return branchId ? 'Chi của người đăng' : 'Cả dòng họ';
}

export const communityApi = {
  posts: (page: number, type?: 'POST' | 'NEWS', familyId?: string) =>
    api.list<Post>('/posts', {
      page,
      size: 20,
      sort: 'createdAt,desc',
      ...(type ? { type } : {}),
      ...(familyId ? { familyId } : {}),
    }),
  /** Bài của một tài khoản cho trang cá nhân; backend vẫn lọc theo người xem được (BR-COM-13). */
  postsByAuthor: (authorId: string, page: number) =>
    api.list<Post>('/posts', { page, size: 10, sort: 'createdAt,desc', authorId }),
  detail: (id: string) => api.get<Post>(`/posts/${encodeURIComponent(id)}`),
  branches: (familyId: string) =>
    api.list<Branch>(`/families/${encodeURIComponent(familyId)}/branches`, { page: 0, size: 100 }),
  /** branchId null là cả dòng họ (SCRUM-101); audience DESCENDANTS là con cháu của tôi (BR-COM-13). */
  create: (
    content: string,
    branchId: string | null,
    familyId?: string,
    audience?: 'FAMILY' | 'BRANCH' | 'DESCENDANTS',
  ) =>
    api.post<Post>(`/posts${familyId ? `?familyId=${encodeURIComponent(familyId)}` : ''}`, {
      content,
      type: 'POST',
      branchId,
      audience,
    } satisfies Schemas['PostCreateRequest']),
  comments: (postId: string, page: number) =>
    api.list<Comment>(`/posts/${encodeURIComponent(postId)}/comments`, {
      page,
      size: 20,
      sort: 'createdAt,desc',
    }),
  /** parentId: trả lời bình luận đó, 1 cấp (BR-COM-14). */
  comment: (postId: string, content: string, parentId?: string) =>
    api.post<Comment>(`/posts/${encodeURIComponent(postId)}/comments`, {
      content,
      parentId: parentId ?? null,
    } satisfies Schemas['CommentRequest']),
  react: (postId: string, type: ReactionType) =>
    api.post(`/posts/${encodeURIComponent(postId)}/reactions`, {
      type,
    } satisfies Schemas['ReactionRequest']),
  removeReaction: (postId: string) => api.delete(`/posts/${encodeURIComponent(postId)}/reactions`),
  /** Sửa nội dung; giữ nguyên người xem (branchId, audience) của bài. */
  updatePost: (post: Post, content: string) =>
    api.patch<Post>(`/posts/${encodeURIComponent(post.id)}`, {
      content,
      branchId: post.branchId ?? null,
      audience: post.audience,
    } satisfies Schemas['PostUpdateRequest']),
  deletePost: (id: string) => api.delete(`/posts/${encodeURIComponent(id)}`),
  updateComment: (id: string, content: string) =>
    api.patch<Comment>(`/comments/${encodeURIComponent(id)}`, {
      content,
    } satisfies Schemas['CommentRequest']),
  deleteComment: (id: string) => api.delete(`/comments/${encodeURIComponent(id)}`),
  report: (
    targetType: Schemas['ContentReportRequest']['targetType'],
    targetId: string,
    reason: string,
  ) =>
    api.post('/content-reports', {
      targetType,
      targetId,
      reason,
    } satisfies Schemas['ContentReportRequest']),
  media: (postId: string, page: number) =>
    api.list<Media>(`/posts/${encodeURIComponent(postId)}/media`, {
      page,
      size: 20,
      sort: 'createdAt,desc',
    }),
  // /media chỉ áp dụng cho PERSON/HERITAGE; bài đăng dùng endpoint riêng trong OpenAPI.
  uploadImage: (postId: string, file: UploadAsset, onProgress?: (percent: number) => void) => {
    const form = new FormData();
    form.append('file', { uri: file.uri, name: file.name, type: file.mimeType } as unknown as Blob);
    return api.upload<Media>(`/posts/${encodeURIComponent(postId)}/media`, form, onProgress);
  },
};
