import { api } from '@/lib/api';
import type { components } from './generated/heritage-directory-dashboard';

type Schemas = components['schemas'];
export type HeritageDocument = Schemas['HeritageDocumentData'];
export type FamilyStory = Schemas['FamilyStoryData'];
export type CreateDocument = Schemas['CreateHeritageDocumentRequest'];
export type CreateStory = Schemas['CreateFamilyStoryRequest'];
export type UpdateDocument = Schemas['UpdateHeritageDocumentRequest'];
export type UpdateStory = Schemas['UpdateFamilyStoryRequest'];

export interface HeritagePage<T> {
  items: T[];
  meta: Schemas['PaginationMeta'];
}

export const heritageApi = {
  documents: (params: { category?: string; familyId?: string; page?: number; size?: number; sort?: string }) =>
    api.list<HeritageDocument>('/heritage/documents', params),
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
    api.post<FamilyStory>(`/heritage/stories${familyId ? `?familyId=${encodeURIComponent(familyId)}` : ''}`, body),
  updateStory: (id: string, body: UpdateStory) =>
    api.patch<FamilyStory>(`/heritage/stories/${id}`, body),
  deleteStory: (id: string) => api.delete(`/heritage/stories/${id}`),
  upload: (file: File, familyId: string) => {
    const body = new FormData();
    body.set('file', file);
    body.set('ownerType', 'HERITAGE');
    body.set('ownerId', familyId);
    return api.upload<{ id: string }>('/media', body);
  },
};
