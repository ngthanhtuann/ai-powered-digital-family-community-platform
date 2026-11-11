import { api } from './api';
import type { components } from './generated/community-events';

type Schemas = components['schemas'];
export type Post = Schemas['Post'];
export type Comment = Schemas['Comment'];
export type Media = Schemas['Media'];
export type ReactionType = Schemas['ReactionRequest']['type'];
export type UploadAsset = { uri: string; name: string; mimeType: string };

export const communityApi = {
  posts: (page: number, type?: 'POST' | 'NEWS', familyId?: string) =>
    api.list<Post>('/posts', {
      page,
      size: 20,
      sort: 'createdAt,desc',
      ...(type ? { type } : {}),
      ...(familyId ? { familyId } : {}),
    }),
  detail: (id: string) => api.get<Post>(`/posts/${encodeURIComponent(id)}`),
  create: (content: string, familyId?: string) =>
    api.post<Post>(`/posts${familyId ? `?familyId=${encodeURIComponent(familyId)}` : ''}`, {
      content,
      type: 'POST',
      branchId: null,
    } satisfies Schemas['PostCreateRequest']),
  comments: (postId: string, page: number) =>
    api.list<Comment>(`/posts/${encodeURIComponent(postId)}/comments`, {
      page,
      size: 20,
      sort: 'createdAt,desc',
    }),
  comment: (postId: string, content: string) =>
    api.post<Comment>(`/posts/${encodeURIComponent(postId)}/comments`, {
      content,
    } satisfies Schemas['CommentRequest']),
  react: (postId: string, type: ReactionType) =>
    api.post(`/posts/${encodeURIComponent(postId)}/reactions`, {
      type,
    } satisfies Schemas['ReactionRequest']),
  removeReaction: (postId: string) => api.delete(`/posts/${encodeURIComponent(postId)}/reactions`),
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
