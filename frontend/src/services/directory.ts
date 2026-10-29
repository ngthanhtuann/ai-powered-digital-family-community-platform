import type { PageParams } from '@/lib/api-types';
import type { components } from '@/services/generated/heritage-directory-dashboard';
import { api } from '@/lib/api';

type Schemas = components['schemas'];

export type DirectoryMember = Schemas['PersonSearchResult'];
export type ProfessionProfile = Schemas['ProfessionProfileData'];
export type EducationProfile = Schemas['EducationProfileData'];
export type ProfessionProfileInput = Schemas['ProfessionProfileRequest'];
export type EducationProfileInput = Schemas['EducationProfileRequest'];

export interface DirectorySearchParams extends PageParams {
  [key: string]: unknown;
  familyId?: string;
  job?: string;
  location?: string;
  generation?: number;
  page: number;
  size: number;
  sort: string;
}

export const directoryApi = {
  members: (params: DirectorySearchParams) =>
    api.list<DirectoryMember>('/directory/members', params),
  professionProfile: (personId: string) =>
    api.get<ProfessionProfile>(`/directory/members/${personId}/profession-profile`),
  saveProfessionProfile: (personId: string, input: ProfessionProfileInput) =>
    api.put<ProfessionProfile>(`/directory/members/${personId}/profession-profile`, input),
  educationProfile: (personId: string) =>
    api.get<EducationProfile>(`/directory/members/${personId}/education-profile`),
  saveEducationProfile: (personId: string, input: EducationProfileInput) =>
    api.put<EducationProfile>(`/directory/members/${personId}/education-profile`, input),
};
