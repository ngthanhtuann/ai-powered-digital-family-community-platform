import { api } from './api';
import type { components } from './generated/heritage-directory-dashboard';

// Danh bạ và Di sản trên mobile chỉ xem (mobile.md mục 2); thêm, sửa làm trên web. Kiểu sinh từ OpenAPI.
type Schemas = components['schemas'];
export type DirectoryMember = Schemas['PersonSearchResult'];
export type EducationProfile = Schemas['EducationProfileData'];
export type HeritageDocument = Schemas['HeritageDocumentData'];
export type FamilyStory = Schemas['FamilyStoryData'];
export type HeritagePhoto = Schemas['PhotoItem'];

export interface DirectoryFilter {
  keyword: string;
  job: string;
  location: string;
  generation: string;
}

/** Bỏ ô trống để không gửi tham số rỗng; thế hệ chỉ gửi khi là số nguyên dương. */
export function directoryParams(filter: DirectoryFilter) {
  const generation = Number(filter.generation);
  return {
    ...(filter.keyword.trim() ? { keyword: filter.keyword.trim() } : {}),
    ...(filter.job.trim() ? { job: filter.job.trim() } : {}),
    ...(filter.location.trim() ? { location: filter.location.trim() } : {}),
    ...(Number.isInteger(generation) && generation > 0 ? { generation } : {}),
  };
}

const family = (familyId?: string | null) => (familyId ? { familyId } : {});

export const heritageApi = {
  members: (filter: DirectoryFilter, page: number, familyId?: string | null) =>
    api.list<DirectoryMember>('/directory/members', {
      page,
      size: 20,
      sort: 'fullName,asc',
      ...directoryParams(filter),
      ...family(familyId),
    }),
  /** 404 khi chưa có hồ sơ học vấn. */
  educationProfile: (personId: string) =>
    api.get<EducationProfile>(
      `/directory/members/${encodeURIComponent(personId)}/education-profile`,
    ),
  documents: (page: number, familyId?: string | null) =>
    api.list<HeritageDocument>('/heritage/documents', {
      page,
      size: 20,
      sort: 'createdAt,desc',
      ...family(familyId),
    }),
  stories: (page: number, familyId?: string | null) =>
    api.list<FamilyStory>('/heritage/stories', {
      page,
      size: 20,
      sort: 'createdAt,desc',
      ...family(familyId),
    }),
  story: (id: string) => api.get<FamilyStory>(`/heritage/stories/${encodeURIComponent(id)}`),
  photos: (page: number, familyId?: string | null) =>
    api.list<HeritagePhoto>('/heritage/photos', {
      page,
      size: 30,
      sort: 'createdAt,desc',
      ...family(familyId),
    }),
};

export type CreateDocument = Schemas['CreateHeritageDocumentRequest'];
export type UpdateDocument = Schemas['UpdateHeritageDocumentRequest'];
export type CreateStory = Schemas['CreateFamilyStoryRequest'];
export type UpdateStory = Schemas['UpdateFamilyStoryRequest'];
export type OutstandingMember = Schemas['OutstandingMemberData'];
export type CreateOutstandingMember = Schemas['CreateOutstandingMemberRequest'];
export type UpdateOutstandingMember = Schemas['UpdateOutstandingMemberRequest'];

const withFamily = (path: string, familyId?: string) =>
  `${path}${familyId ? `?familyId=${encodeURIComponent(familyId)}` : ''}`;

/** Thêm, sửa, xóa di sản và người tiêu biểu (BR-HER-01, 04: chỉ trưởng chi, trưởng họ, quản trị), như web. */
export const heritageManageApi = {
  document: (id: string) =>
    api.get<HeritageDocument>(`/heritage/documents/${encodeURIComponent(id)}`),
  createDocument: (body: CreateDocument, familyId?: string) =>
    api.post<HeritageDocument>(withFamily('/heritage/documents', familyId), body),
  updateDocument: (id: string, body: UpdateDocument) =>
    api.patch<HeritageDocument>(`/heritage/documents/${encodeURIComponent(id)}`, body),
  deleteDocument: (id: string) => api.delete(`/heritage/documents/${encodeURIComponent(id)}`),
  createStory: (body: CreateStory, familyId?: string) =>
    api.post<FamilyStory>(withFamily('/heritage/stories', familyId), body),
  updateStory: (id: string, body: UpdateStory) =>
    api.patch<FamilyStory>(`/heritage/stories/${encodeURIComponent(id)}`, body),
  deleteStory: (id: string) => api.delete(`/heritage/stories/${encodeURIComponent(id)}`),
  outstandingMembers: (page: number, familyId?: string | null) =>
    api.list<OutstandingMember>('/heritage/outstanding-members', {
      page,
      size: 20,
      sort: 'createdAt,desc',
      ...(familyId ? { familyId } : {}),
    }),
  createOutstandingMember: (body: CreateOutstandingMember, familyId?: string) =>
    api.post<OutstandingMember>(withFamily('/heritage/outstanding-members', familyId), body),
  updateOutstandingMember: (id: string, body: UpdateOutstandingMember) =>
    api.patch<OutstandingMember>(`/heritage/outstanding-members/${encodeURIComponent(id)}`, body),
  deleteOutstandingMember: (id: string) =>
    api.delete(`/heritage/outstanding-members/${encodeURIComponent(id)}`),
  /** Ảnh hoặc PDF cho di sản (ownerType HERITAGE, ownerId là gia đình). */
  upload: (
    file: { uri: string; name: string; mimeType: string },
    familyId: string,
    caption?: string,
  ) => {
    const form = new FormData();
    form.append('file', { uri: file.uri, name: file.name, type: file.mimeType } as unknown as Blob);
    form.append('ownerType', 'HERITAGE');
    form.append('ownerId', familyId);
    if (caption?.trim()) form.append('caption', caption.trim());
    return api.upload<{ id: string }>('/media', form);
  },
};
