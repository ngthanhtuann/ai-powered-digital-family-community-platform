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
