import { api } from './api';
import type { UploadAsset } from './community';
import type { operations as AiOperations } from './generated/ai';
import type { components } from './generated/genealogy';
import type { components as DirectoryComponents } from './generated/heritage-directory-dashboard';

// Kiểu sinh từ genealogy.yaml, ai.yaml, heritage-directory-dashboard.yaml (npm run gen:api), không viết tay.
type Schemas = components['schemas'];
export type Person = Schemas['Person'];
export type TreeNode = Schemas['TreeNode'];
export type TreeEdge = Schemas['TreeEdge'];
export type TreeData = NonNullable<Schemas['TreeResponse']['data']>;
export type Relationship = NonNullable<Schemas['RelationshipResponse']['data']>;
export type ProfessionProfile = DirectoryComponents['schemas']['ProfessionProfileData'];
export type PersonInput = Schemas['PersonInput'];
type ProfessionInput = DirectoryComponents['schemas']['ProfessionProfileRequest'];
type EducationInput = DirectoryComponents['schemas']['EducationProfileRequest'];
type ExplainOperation = AiOperations['explainRelationship'];
export type RelationshipExplanation =
  ExplainOperation['responses'][200]['content']['application/json']['data'];

// LLM có thể chậm hơn timeout mặc định của client (giống services/ai.ts).
const AI_TIMEOUT_MS = 45_000;

/** Mobile xem gia phả; chính chủ sửa hồ sơ của mình (BR-GEN-20, SCRUM-128). Thêm, sửa người khác và quan hệ làm trên web (mobile.md mục 2). */
export const genealogyApi = {
  tree: (familyId: string, params: { rootId?: string; depth?: number } = {}) =>
    api.get<TreeData>(`/families/${encodeURIComponent(familyId)}/tree`, params),
  persons: (familyId: string, q: string, page = 0) =>
    api.list<Person>(`/families/${encodeURIComponent(familyId)}/persons`, {
      page,
      size: 20,
      ...(q.trim() ? { q: q.trim() } : {}),
    }),
  person: (personId: string) => api.get<Person>(`/persons/${encodeURIComponent(personId)}`),
  /** Hồ sơ gắn với tài khoản đang đăng nhập; 404 khi chưa gắn. */
  myPerson: () => api.get<Person>('/persons/me'),
  /** PUT ghi đè mọi trường: gửi lại cả trường không sửa (dùng toPersonInput). */
  updatePerson: (personId: string, input: PersonInput) =>
    api.put<Person>(`/persons/${encodeURIComponent(personId)}`, input),
  /** Ảnh đại diện: tải lên /media rồi gán avatarMediaId bằng updatePerson. */
  uploadPersonPhoto: (personId: string, file: UploadAsset) => {
    const form = new FormData();
    form.append('file', { uri: file.uri, name: file.name, type: file.mimeType } as unknown as Blob);
    form.append('ownerType', 'PERSON');
    form.append('ownerId', personId);
    return api.upload<{ id: string }>('/media', form);
  },
  saveProfession: (personId: string, input: ProfessionInput) =>
    api.put<ProfessionProfile>(
      `/directory/members/${encodeURIComponent(personId)}/profession-profile`,
      input,
    ),
  saveEducation: (personId: string, input: EducationInput) =>
    api.put(`/directory/members/${encodeURIComponent(personId)}/education-profile`, input),
  relationship: (personA: string, personB: string) =>
    api.get<Relationship>('/relationships', { personA, personB }),
  /** 404 khi người này chưa có hồ sơ nghề (heritage-directory-dashboard.yaml). */
  professionProfile: (personId: string) =>
    api.get<ProfessionProfile>(
      `/directory/members/${encodeURIComponent(personId)}/profession-profile`,
    ),
  explain: (personAId: string, personBId: string) =>
    api.post<RelationshipExplanation>(
      '/ai/relationship-explanations',
      { personAId, personBId },
      { timeout: AI_TIMEOUT_MS },
    ),
};

/** Hồ sơ hiện có thành input đầy đủ, như web (PUT /persons ghi đè mọi trường). */
export function toPersonInput(person: Person): PersonInput {
  return {
    fullName: person.fullName ?? '',
    gender: person.gender ?? 'OTHER',
    birthDate: person.birthDate ?? null,
    birthYear: person.birthDate ? null : (person.birthYear ?? null),
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

/** "1950 – 1998" hoặc "1950"; người đã mất không rõ năm mất ghi "đã mất". Năm bị ẩn (BR-GEN-10) thì bỏ trống. */
export function lifeSpan(
  birthYear?: number | null,
  isDeceased?: boolean,
  deathDate?: string | null,
): string {
  const born = birthYear ? String(birthYear) : '';
  if (!isDeceased) return born;
  const died = deathDate ? deathDate.slice(0, 4) : 'đã mất';
  return born ? `${born} – ${died}` : died;
}
