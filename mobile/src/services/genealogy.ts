import { api } from './api';
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
type ExplainOperation = AiOperations['explainRelationship'];
export type RelationshipExplanation =
  ExplainOperation['responses'][200]['content']['application/json']['data'];

// LLM có thể chậm hơn timeout mặc định của client (giống services/ai.ts).
const AI_TIMEOUT_MS = 45_000;

/** Mobile chỉ xem gia phả; thêm, sửa thành viên và quan hệ làm trên web (mobile.md mục 2). */
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
