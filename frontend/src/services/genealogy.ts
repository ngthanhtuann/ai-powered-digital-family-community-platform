import type { components } from '@/services/generated/genealogy';
import { api } from '@/lib/api';

type Schemas = components['schemas'];

export type Person = Schemas['Person'];
export type PersonInput = Schemas['PersonInput'];
export type ParentInput = Schemas['ParentInput'];
export type MarriageInput = Schemas['MarriageInput'];
export type MarriageUpdate = Schemas['MarriageUpdate'];
export type ParentChild = Schemas['ParentChild'];
export type Marriage = Schemas['Marriage'];

/** API Gia phả theo genealogy.yaml (UC-GEN-03, 04, 05). */
export const genealogyApi = {
  persons: (familyId: string, params: { q?: string; page: number; size: number; sort?: string }) =>
    api.list<Person>(`/families/${familyId}/persons`, params),
  createPerson: (familyId: string, input: PersonInput) => api.post<Person>(`/families/${familyId}/persons`, input),
  updatePerson: (personId: string, input: PersonInput) => api.put<Person>(`/persons/${personId}`, input),
  addParent: (childId: string, input: ParentInput) => api.post<ParentChild>(`/persons/${childId}/parents`, input),
  removeParent: (childId: string, parentId: string) => api.delete(`/persons/${childId}/parents/${parentId}`),
  createMarriage: (input: MarriageInput) => api.post<Marriage>('/marriages', input),
  updateMarriage: (marriageId: string, input: MarriageUpdate) => api.put<Marriage>(`/marriages/${marriageId}`, input),
  deleteMarriage: (marriageId: string) => api.delete(`/marriages/${marriageId}`),
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
