import type { components } from '@/services/generated/genealogy';
import { api } from '@/lib/api';

type Schemas = components['schemas'];

export type Person = Schemas['Person'];
export type PersonInput = Schemas['PersonInput'];
export type DeathInput = Schemas['DeathInput'];
export type AccountCandidate = Schemas['AccountCandidate'];
export type AccountReplacementInput = Schemas['AccountReplacementInput'];
export type ParentInput = Schemas['ParentInput'];
export type MarriageInput = Schemas['MarriageInput'];
export type MarriageUpdate = Schemas['MarriageUpdate'];
export type ParentChild = Schemas['ParentChild'];
export type Marriage = Schemas['Marriage'];
export type TreeNode = Schemas['TreeNode'];
export type TreeEdge = Schemas['TreeEdge'];
export type TreeData = NonNullable<Schemas['TreeResponse']['data']>;
export type Relationship = NonNullable<Schemas['RelationshipResponse']['data']>;

/** API Gia phả theo genealogy.yaml (UC-GEN-03 đến 08). */
export const genealogyApi = {
  persons: (familyId: string, params: { q?: string; page: number; size: number; sort?: string }) =>
    api.list<Person>(`/families/${familyId}/persons`, params),
  createPerson: (familyId: string, input: PersonInput) => api.post<Person>(`/families/${familyId}/persons`, input),
  updatePerson: (personId: string, input: PersonInput) => api.put<Person>(`/persons/${personId}`, input),
  /** BR-GEN-21: người quản lý báo mất hoặc khôi phục khi báo nhầm. */
  setDeath: (personId: string, input: DeathInput) => api.put<Person>(`/persons/${personId}/death`, input),
  /** BR-GEN-23: thay tài khoản khi người thân mất tài khoản. */
  accountCandidates: (personId: string) => api.get<AccountCandidate[]>(`/persons/${personId}/account-candidates`),
  replaceAccount: (personId: string, input: AccountReplacementInput) =>
    api.post<Person>(`/persons/${personId}/account-replacements`, input),
  addParent: (childId: string, input: ParentInput) => api.post<ParentChild>(`/persons/${childId}/parents`, input),
  removeParent: (childId: string, parentId: string) => api.delete(`/persons/${childId}/parents/${parentId}`),
  createMarriage: (input: MarriageInput) => api.post<Marriage>('/marriages', input),
  updateMarriage: (marriageId: string, input: MarriageUpdate) => api.put<Marriage>(`/marriages/${marriageId}`, input),
  deleteMarriage: (marriageId: string) => api.delete(`/marriages/${marriageId}`),
  tree: (familyId: string, params: { rootId?: string; depth?: number } = {}) =>
    api.get<TreeData>(`/families/${familyId}/tree`, params),
  person: (personId: string) => api.get<Person>(`/persons/${personId}`),
  /** Hồ sơ gắn với tài khoản đang đăng nhập; 404 khi chưa gắn (SCRUM-117). */
  myPerson: () => api.get<Person>('/persons/me'),
  relationship: (personA: string, personB: string) => api.get<Relationship>('/relationships', { personA, personB }),
  /** Ảnh qua API có kiểm quyền (tệp không có URL công khai). */
  mediaContent: (mediaId: string) => api.download(`/media/${mediaId}/content`),
  /** Tải ảnh lên hồ sơ (ownerType PERSON), rồi gán làm ảnh đại diện bằng PUT /persons/{id} (SCRUM-119). */
  uploadPersonPhoto: (personId: string, file: File) => {
    const body = new FormData();
    body.set('file', file);
    body.set('ownerType', 'PERSON');
    body.set('ownerId', personId);
    return api.upload<{ id: string }>('/media', body);
  },
};

/** Dữ liệu hồ sơ hiện có thành input đầy đủ: PUT /persons ghi đè mọi trường nên phải gửi lại trường không sửa. */
export function toPersonInput(person: Person): PersonInput {
  return {
    fullName: person.fullName ?? '',
    gender: person.gender ?? 'OTHER',
    birthDate: person.birthDate ?? null,
    birthYear: person.birthDate ? null : person.birthYear ?? null,
    birthplace: person.birthplace ?? null,
    currentProvince: person.currentProvince ?? null,
    currentAddress: person.currentAddress ?? null,
    branchId: person.branchId ?? null,
    isDeceased: person.isDeceased ?? false,
    deathDate: person.deathDate ?? null,
    burialPlace: person.burialPlace ?? null,
    avatarMediaId: person.avatarMediaId ?? null,
    note: person.note ?? null,
  };
}
