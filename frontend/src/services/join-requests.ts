// Xin tham gia gia đình và duyệt (auth.yaml: Membership; UC-AUTH-06, 07). Kiểu sinh từ OpenAPI.
import { api } from '@/lib/api';
import type { components } from './generated/auth';
import type { components as Genealogy } from './generated/genealogy';

type Schemas = components['schemas'];
export type JoinRequest = Schemas['JoinRequest'];
export type JoinRequestStatus = Schemas['JoinRequestStatus'];
export type JoinRequestCreate = Schemas['JoinRequestCreateRequest'];
export type JoinRequestReview = Schemas['JoinRequestReviewRequest'];
type FamilyInput = Genealogy['schemas']['FamilyInput'];
type Family = Genealogy['schemas']['Family'];

export const JOIN_PAGE_SIZE = 20;

export const joinRequestsApi = {
  create: (body: JoinRequestCreate) => api.post<JoinRequest>('/join-requests', body),
  cancel: (id: string) => api.delete(`/join-requests/${id}`),
  list: (params: { status: JoinRequestStatus; familyId?: string; page: number }) =>
    api.list<JoinRequest>('/join-requests', { ...params, size: JOIN_PAGE_SIZE, sort: 'createdAt,desc' }),
  review: (id: string, body: JoinRequestReview) => api.patch<JoinRequest>(`/join-requests/${id}`, body),
  /** UC-GEN-01: người chưa thuộc gia đình tự lập gia đình, trở thành trưởng họ (BR-AUTH-12). */
  createFamily: (body: FamilyInput) => api.post<Family>('/families', body),
};
