import { api } from '@/lib/api';
import type { components as Community } from '@/services/generated/community-events';
import type { components as Auth } from '@/services/generated/auth';

export type Post = Community['schemas']['Post'];
export type Comment = Community['schemas']['Comment'];
export type Reaction = Community['schemas']['Reaction'];
export type PostType = Community['schemas']['PostCreateRequest']['type'];
export type ReactionType = Community['schemas']['ReactionRequest']['type'];
export type Media = Community['schemas']['Media'];
export type Membership = Auth['schemas']['Membership'];
export type User = Auth['schemas']['User'];

export const communityApi = {
  membership: () => api.get<Membership>('/users/me/membership'),
  user: () => api.get<User>('/users/me'),
  posts: (page: number, type?: 'POST' | 'NEWS', familyId?: string) =>
    api.list<Post>('/posts', {
      page,
      size: 20,
      sort: 'createdAt,desc',
      ...(type ? { type } : {}),
      ...(familyId ? { familyId } : {}),
    }),
  createPost: (content: string, type: PostType, familyId?: string) =>
    api.post<Post>('/posts' + (familyId ? '?familyId=' + encodeURIComponent(familyId) : ''), {
      content,
      type,
    } satisfies Community['schemas']['PostCreateRequest']),
  updatePost: (id: string, content: string) =>
    api.patch<Post>('/posts/' + id, { content } satisfies Community['schemas']['PostUpdateRequest']),
  deletePost: (id: string) => api.delete('/posts/' + id),
  comments: (postId: string, page: number) =>
    api.list<Comment>('/posts/' + postId + '/comments', {
      page,
      size: 20,
      sort: 'createdAt,desc',
    }),
  createComment: (postId: string, content: string) =>
    api.post<Comment>('/posts/' + postId + '/comments', {
      content,
    } satisfies Community['schemas']['CommentRequest']),
  updateComment: (id: string, content: string) =>
    api.patch<Comment>('/comments/' + id, {
      content,
    } satisfies Community['schemas']['CommentRequest']),
  deleteComment: (id: string) => api.delete('/comments/' + id),
  react: (postId: string, type: ReactionType) =>
    api.post<Reaction>('/posts/' + postId + '/reactions', {
      type,
    } satisfies Community['schemas']['ReactionRequest']),
  removeReaction: (postId: string) => api.delete('/posts/' + postId + '/reactions'),
  postMedia: (postId: string, page: number) =>
    api.list<Media>('/posts/' + postId + '/media', { page, size: 20, sort: 'createdAt,desc' }),
  uploadPostMedia: (postId: string, file: File, onProgress?: (percent: number) => void) => {
    const form = new FormData();
    form.set('file', file);
    return api.upload<Media>('/posts/' + postId + '/media', form, onProgress);
  },
};
