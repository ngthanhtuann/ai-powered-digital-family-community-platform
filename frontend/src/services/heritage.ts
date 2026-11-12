import { api } from '@/lib/api';
import type { components } from './generated/heritage-directory-dashboard';

type Schemas = components['schemas'];
export type HeritageDocument = Schemas['HeritageDocumentData'];
export type FamilyStory = Schemas['FamilyStoryData'];
export type CreateDocument = Schemas['CreateHeritageDocumentRequest'];
export type CreateStory = Schemas['CreateFamilyStoryRequest'];
export type UpdateDocument = Schemas['UpdateHeritageDocumentRequest'];
export type UpdateStory = Schemas['UpdateFamilyStoryRequest'];
export type HeritagePhoto = Schemas['PhotoItem'];
export type OutstandingMember = Schemas['OutstandingMemberData'];
export type CreateOutstandingMember = Schemas['CreateOutstandingMemberRequest'];
export type UpdateOutstandingMember = Schemas['UpdateOutstandingMemberRequest'];

export interface HeritagePage<T> {
  items: T[];
  meta: Schemas['PaginationMeta'];
}

export function heritageDownloadName(title: string, blob: Blob) {
  const extension =
    blob.type === 'application/pdf'
      ? '.pdf'
      : blob.type === 'image/jpeg'
        ? '.jpg'
        : blob.type === 'image/png'
          ? '.png'
          : blob.type === 'image/webp'
            ? '.webp'
            : '';
  return /\.[a-z0-9]{2,5}$/i.test(title) ? title : `${title}${extension}`;
}

export const heritageApi = {
  photos: (params: {
    familyId?: string;
    mediaId?: string;
    page?: number;
    size?: number;
    sort?: string;
  }) => api.list<HeritagePhoto>('/heritage/photos', params),
  documents: (params: {
    category?: string;
    familyId?: string;
    page?: number;
    size?: number;
    sort?: string;
  }) => api.list<HeritageDocument>('/heritage/documents', params),
  document: (id: string) => api.get<HeritageDocument>(`/heritage/documents/${id}`),
  createDocument: (body: CreateDocument, familyId?: string) =>
    api.post<HeritageDocument>(
      `/heritage/documents${familyId ? `?familyId=${encodeURIComponent(familyId)}` : ''}`,
      body,
    ),
  updateDocument: (id: string, body: UpdateDocument) =>
    api.patch<HeritageDocument>(`/heritage/documents/${id}`, body),
  deleteDocument: (id: string) => api.delete(`/heritage/documents/${id}`),
  stories: (params: { page?: number; size?: number; sort?: string; familyId?: string }) =>
    api.list<FamilyStory>('/heritage/stories', params),
  story: (id: string) => api.get<FamilyStory>(`/heritage/stories/${id}`),
  createStory: (body: CreateStory, familyId?: string) =>
    api.post<FamilyStory>(
      `/heritage/stories${familyId ? `?familyId=${encodeURIComponent(familyId)}` : ''}`,
      body,
    ),
  updateStory: (id: string, body: UpdateStory) =>
    api.patch<FamilyStory>(`/heritage/stories/${id}`, body),
  deleteStory: (id: string) => api.delete(`/heritage/stories/${id}`),
  outstandingMembers: (params: { familyId?: string; page?: number; size?: number; sort?: string }) =>
    api.list<OutstandingMember>('/heritage/outstanding-members', params),
  createOutstandingMember: (body: CreateOutstandingMember, familyId?: string) =>
    api.post<OutstandingMember>(
      `/heritage/outstanding-members${familyId ? `?familyId=${encodeURIComponent(familyId)}` : ''}`,
      body,
    ),
  updateOutstandingMember: (id: string, body: UpdateOutstandingMember) =>
    api.patch<OutstandingMember>(`/heritage/outstanding-members/${id}`, body),
  deleteOutstandingMember: (id: string) => api.delete(`/heritage/outstanding-members/${id}`),
  /** contentUrl dạng /api/v1/media/{id}/content; client đã có tiền tố /api/v1. */
  file: (contentUrl: string) => api.download(contentUrl.replace(/^\/api\/v1/, '')),
  upload: (file: File, familyId: string, caption?: string) => {
    const body = new FormData();
    body.set('file', file);
    body.set('ownerType', 'HERITAGE');
    body.set('ownerId', familyId);
    if (caption?.trim()) body.set('caption', caption.trim());
    return api.upload<{ id: string }>('/media', body);
  },
};
