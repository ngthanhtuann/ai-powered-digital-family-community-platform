// Gọi API quản trị (auth.yaml: Admin, Moderation, Audit, FamilyRoles; UC-AUTH-08, 09, 10, 14). Kiểu sinh từ OpenAPI.
import { api } from '@/lib/api';
import type { components } from './generated/auth';
import type { components as Genealogy } from './generated/genealogy';

type Schemas = components['schemas'];
export type AdminUser = Schemas['AdminUser'];
export type UserStatus = Schemas['UserStatus'];
export type GlobalRole = Schemas['GlobalRole'];
export type MemberRole = Schemas['MemberRole'];
export type ContentReport = Schemas['ContentReport'];
export type ReportStatus = Schemas['ReportStatus'];
export type ContentType = Schemas['ContentType'];
export type ReviewAction = Schemas['ContentReportReviewRequest']['action'];
export type FamilyAccount = Schemas['FamilyAccount'];
export type MembershipRequest = Schemas['AdminMembershipRequest'];
export type BackupFile = Schemas['BackupFile'];
export type RestoreResult = Schemas['RestoreResult'];
export type AuditLog = Schemas['AuditLog'];
export type SystemConfig = Schemas['SystemConfig'];

export const PAGE_SIZE = 20;

export const adminApi = {
  users: (params: { q?: string; status?: UserStatus; role?: GlobalRole; page: number }) =>
    api.list<AdminUser>('/admin/users', { ...params, size: PAGE_SIZE, sort: 'createdAt,desc' }),
  setStatus: (userId: string, body: Schemas['AdminUserStatusRequest']) =>
    api.patch<AdminUser>(`/admin/users/${userId}`, body),
  setRoles: (userId: string, roles: GlobalRole[]) =>
    api.put<AdminUser>(`/admin/users/${userId}/roles`, { roles } satisfies Schemas['AdminUserRolesRequest']),
  setMembership: (userId: string, body: MembershipRequest) =>
    api.put<AdminUser>(`/admin/users/${userId}/membership`, body),
  reports: (params: { status: ReportStatus; targetType?: ContentType; page: number }) =>
    api.list<ContentReport>('/admin/content-reports', { ...params, size: PAGE_SIZE, sort: 'createdAt,desc' }),
  review: (id: string, action: ReviewAction) =>
    api.patch<ContentReport>(`/admin/content-reports/${id}`, {
      action,
    } satisfies Schemas['ContentReportReviewRequest']),
  backups: (page: number) => api.list<BackupFile>('/admin/backups', { page, size: PAGE_SIZE }),
  createBackup: () => api.post<BackupFile>('/admin/backups'),
  restore: (backupId: string, confirmText: 'RESTORE') =>
    api.post<RestoreResult>('/admin/restores', { backupId, confirmText } satisfies Schemas['RestoreCreateRequest']),
  auditLogs: (params: { actorUserId?: string; action?: string; from?: string; to?: string; page: number }) =>
    api.list<AuditLog>('/admin/audit-logs', { ...params, size: PAGE_SIZE, sort: 'createdAt,desc' }),
  /** UC-AUTH-12: số khóa nhỏ nên API không phân trang. */
  configs: () => api.list<SystemConfig>('/admin/configs'),
  updateConfig: (key: string, value: string) =>
    api.put<SystemConfig>(`/admin/configs/${encodeURIComponent(key)}`, {
      value,
    } satisfies Schemas['ConfigUpdateRequest']),
};

/** UC-AUTH-14: trưởng họ (hoặc quản trị) phân công trưởng chi. */
export const familyRolesApi = {
  accounts: (familyId: string, page: number) =>
    api.list<FamilyAccount>(`/families/${familyId}/accounts`, { page, size: PAGE_SIZE }),
  setRole: (familyId: string, userId: string, body: Schemas['FamilyAccountRoleRequest']) =>
    api.put<FamilyAccount>(`/families/${familyId}/accounts/${userId}`, body),
};

/** BR-AUTH-22: BRANCH_ADMIN không gán chi hiển thị là "Trưởng họ", có gán chi là "Trưởng chi <tên chi>". */
export function memberRoleLabel(role?: MemberRole | null, branchId?: string | null, branchName?: string | null) {
  if (role !== 'BRANCH_ADMIN') return 'Thành viên';
  if (!branchId) return 'Trưởng họ';
  return branchName ? `Trưởng chi ${branchName}` : 'Trưởng chi';
}

type Branch = Genealogy['schemas']['Branch'];

/** Chi của dòng họ để chọn khi gán trưởng chi (genealogy.yaml listBranches). */
export const branchesOf = (familyId: string) =>
  api.list<Branch>(`/families/${familyId}/branches`, { page: 0, size: 100 });
