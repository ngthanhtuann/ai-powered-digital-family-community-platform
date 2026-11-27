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

export type DeathInput = Schemas['DeathInput'];
export type RelativeInput = Schemas['RelativeInput'];
export type BranchHead = Schemas['BranchHead'];
export type BranchInput = Schemas['BranchInput'];
export type Branch = Schemas['Branch'];
export type AccountCandidate = Schemas['AccountCandidate'];
export type DeathReport = Schemas['DeathReport'];
export type DeathReportInput = Schemas['DeathReportInput'];
export type AccountReplacementInput = Schemas['AccountReplacementInput'];
export type ParentInput = Schemas['ParentInput'];
export type MarriageInput = Schemas['MarriageInput'];
export type MarriageUpdate = Schemas['MarriageUpdate'];
export type FamilyInput = Schemas['FamilyInput'];
export type Family = Schemas['Family'];

const enc = encodeURIComponent;

/** Quản lý gia phả như web (UC-GEN-02 đến 05, BR-GEN-21 đến 30); quyền do server kiểm (BR-GEN-14). */
export const genealogyManageApi = {
  persons: (familyId: string, params: { q?: string; page: number; size: number; sort?: string }) =>
    api.list<Person>(`/families/${enc(familyId)}/persons`, params),
  createPerson: (familyId: string, input: PersonInput) =>
    api.post<Person>(`/families/${enc(familyId)}/persons`, input),
  /** BR-GEN-21: người quản lý báo mất hoặc khôi phục khi báo nhầm. */
  setDeath: (personId: string, input: DeathInput) =>
    api.put<Person>(`/persons/${enc(personId)}/death`, input),
  /** BR-GEN-26: tạo người thân mới và nối quan hệ trong một lần lưu. */
  addRelative: (personId: string, input: RelativeInput) =>
    api.post<Person>(`/persons/${enc(personId)}/relatives`, input),
  /** BR-GEN-24: con ruột báo mất, người khác xác nhận. */
  reportDeath: (personId: string, input: DeathReportInput) =>
    api.post<DeathReport>(`/persons/${enc(personId)}/death-reports`, input),
  latestDeathReport: (personId: string) =>
    api.get<DeathReport | null>(`/persons/${enc(personId)}/death-reports/latest`),
  confirmDeathReport: (reportId: string) =>
    api.post<DeathReport>(`/death-reports/${enc(reportId)}/confirmations`),
  /** BR-GEN-23: thay tài khoản khi người thân mất tài khoản. */
  accountCandidates: (personId: string) =>
    api.get<AccountCandidate[]>(`/persons/${enc(personId)}/account-candidates`),
  replaceAccount: (personId: string, input: AccountReplacementInput) =>
    api.post<Person>(`/persons/${enc(personId)}/account-replacements`, input),
  addParent: (childId: string, input: ParentInput) =>
    api.post(`/persons/${enc(childId)}/parents`, input),
  removeParent: (childId: string, parentId: string) =>
    api.delete(`/persons/${enc(childId)}/parents/${enc(parentId)}`),
  createMarriage: (input: MarriageInput) => api.post('/marriages', input),
  updateMarriage: (marriageId: string, input: MarriageUpdate) =>
    api.put(`/marriages/${enc(marriageId)}`, input),
  deleteMarriage: (marriageId: string) => api.delete(`/marriages/${enc(marriageId)}`),
  branches: (familyId: string) =>
    api.list<Branch>(`/families/${enc(familyId)}/branches`, {
      page: 0,
      size: 100,
      sort: 'name,asc',
    }),
  /** UC-GEN-02, BR-GEN-30: người lập chi chưa thuộc chi nào thì vào chi. */
  createBranch: (familyId: string, input: BranchInput) =>
    api.post(`/families/${enc(familyId)}/branches`, input),
  updateBranch: (branchId: string, input: BranchInput) =>
    api.put(`/branches/${enc(branchId)}`, input),
  /** BR-GEN-29: trưởng chi của từng chi (chọn tay hoặc theo dòng). */
  branchHeads: (familyId: string) =>
    api.get<BranchHead[]>(`/families/${enc(familyId)}/branch-heads`),
  setBranchHead: (branchId: string, personId: string | null) =>
    api.put<BranchHead>(`/branches/${enc(branchId)}/head`, { personId }),
  /** UC-GEN-01: người chưa thuộc gia đình tự lập gia đình, trở thành trưởng họ (BR-AUTH-12). */
  createFamily: (input: FamilyInput) => api.post<Family>('/families', input),
};

/** Năm sinh để hiển thị và so tuổi cha mẹ – con (BR-GEN-04). */
export const yearOf = (p: { birthYear?: number | null; birthDate?: string | null }) =>
  p.birthYear ?? (p.birthDate ? Number(p.birthDate.slice(0, 4)) : undefined);
