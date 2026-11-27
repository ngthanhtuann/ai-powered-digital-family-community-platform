// Quản lý và quản trị trên mobile, chép theo frontend/src/services/admin.ts, join-requests.ts, dashboard.ts.
// Kiểu sinh từ auth.yaml, heritage-directory-dashboard.yaml, genealogy.yaml (npm run gen:api), không viết tay.
import { api, downloadAndOpen } from './api';
import type { components } from './generated/auth';
import type { components as Genealogy } from './generated/genealogy';
import type { components as Dashboard } from './generated/heritage-directory-dashboard';

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
export type AuditLog = Schemas['AuditLog'];
export type SystemConfig = Schemas['SystemConfig'];
export type JoinRequest = Schemas['JoinRequest'];
export type JoinRequestStatus = Schemas['JoinRequestStatus'];
export type JoinRequestReview = Schemas['JoinRequestReviewRequest'];
export type DashboardStatistics = Dashboard['schemas']['DashboardStatisticsData'];
export type DashboardFamily = Genealogy['schemas']['Family'];
export type DashboardReportType = 'GENEALOGY' | 'COMMUNITY' | 'EVENTS';

export const PAGE_SIZE = 20;
const enc = encodeURIComponent;

/** UC-AUTH-08 đến 12: chỉ SYSTEM_ADMIN (server kiểm PERM_001). */
export const adminApi = {
  users: (params: { q?: string; status?: UserStatus; role?: GlobalRole; page: number }) =>
    api.list<AdminUser>('/admin/users', { ...params, size: PAGE_SIZE, sort: 'createdAt,desc' }),
  setStatus: (userId: string, body: Schemas['AdminUserStatusRequest']) =>
    api.patch<AdminUser>(`/admin/users/${enc(userId)}`, body),
  setRoles: (userId: string, roles: GlobalRole[]) =>
    api.put<AdminUser>(`/admin/users/${enc(userId)}/roles`, {
      roles,
    } satisfies Schemas['AdminUserRolesRequest']),
  setMembership: (userId: string, body: MembershipRequest) =>
    api.put<AdminUser>(`/admin/users/${enc(userId)}/membership`, body),
  reports: (params: { status: ReportStatus; targetType?: ContentType; page: number }) =>
    api.list<ContentReport>('/admin/content-reports', {
      ...params,
      size: PAGE_SIZE,
      sort: 'createdAt,desc',
    }),
  review: (id: string, action: ReviewAction) =>
    api.patch<ContentReport>(`/admin/content-reports/${enc(id)}`, {
      action,
    } satisfies Schemas['ContentReportReviewRequest']),
  backups: (page: number) => api.list<BackupFile>('/admin/backups', { page, size: PAGE_SIZE }),
  createBackup: () => api.post<BackupFile>('/admin/backups'),
  restore: (backupId: string, confirmText: 'RESTORE') =>
    api.post('/admin/restores', {
      backupId,
      confirmText,
    } satisfies Schemas['RestoreCreateRequest']),
  auditLogs: (params: {
    actorUserId?: string;
    familyId?: string;
    action?: string;
    from?: string;
    to?: string;
    page: number;
  }) =>
    api.list<AuditLog>('/admin/audit-logs', { ...params, size: PAGE_SIZE, sort: 'createdAt,desc' }),
  /** UC-AUTH-12: số khóa nhỏ nên API không phân trang. */
  configs: () => api.list<SystemConfig>('/admin/configs'),
  updateConfig: (key: string, value: string) =>
    api.put<SystemConfig>(`/admin/configs/${enc(key)}`, {
      value,
    } satisfies Schemas['ConfigUpdateRequest']),
};

/** UC-AUTH-14: trưởng họ (hoặc quản trị) phân công trưởng chi. */
export const familyRolesApi = {
  accounts: (familyId: string, page: number, size = PAGE_SIZE) =>
    api.list<FamilyAccount>(`/families/${enc(familyId)}/accounts`, { page, size }),
  setRole: (familyId: string, userId: string, body: Schemas['FamilyAccountRoleRequest']) =>
    api.put<FamilyAccount>(`/families/${enc(familyId)}/accounts/${enc(userId)}`, body),
};

/** UC-AUTH-06, 07: xin tham gia, hủy, duyệt. */
export const joinRequestsApi = {
  create: (body: Schemas['JoinRequestCreateRequest']) =>
    api.post<JoinRequest>('/join-requests', body),
  cancel: (id: string) => api.delete(`/join-requests/${enc(id)}`),
  list: (params: { status: JoinRequestStatus; familyId?: string; page: number }) =>
    api.list<JoinRequest>('/join-requests', { ...params, size: PAGE_SIZE, sort: 'createdAt,desc' }),
  review: (id: string, body: JoinRequestReview) =>
    api.patch<JoinRequest>(`/join-requests/${enc(id)}`, body),
};

/** BR-AUTH-22: BRANCH_ADMIN không gán chi là "Trưởng họ", có gán chi là "Trưởng chi <tên chi>". */
export function memberRoleLabel(
  role?: MemberRole | null,
  branchId?: string | null,
  branchName?: string | null,
) {
  if (role !== 'BRANCH_ADMIN') return 'Thành viên';
  if (!branchId) return 'Trưởng họ';
  return branchName ? `Trưởng chi ${branchName}` : 'Trưởng chi';
}

async function allPages<T>(url: string, params: Record<string, unknown> = {}): Promise<T[]> {
  const items: T[] = [];
  let page = 0;
  let total = Number.POSITIVE_INFINITY;
  while (items.length < total) {
    const result = await api.list<T>(url, { ...params, page, size: 100 });
    items.push(...result.items);
    total = result.meta.total;
    if (result.items.length === 0) break; // phòng total sai: không lặp vô hạn
    page += 1;
  }
  return items;
}

function scopeQuery(params: { familyId?: string; branchId?: string }) {
  const parts = [
    params.familyId ? `familyId=${enc(params.familyId)}` : '',
    params.branchId ? `branchId=${enc(params.branchId)}` : '',
  ].filter(Boolean);
  return parts.join('&');
}

/** FR-DSH-01 đến 05: chỉ trưởng chi, trưởng họ, quản trị (BR-DSH-01, 03). */
export const dashboardApi = {
  statistics: (params: { familyId?: string; branchId?: string }) =>
    api.get<DashboardStatistics>('/dashboard/statistics', params),
  /** Danh sách gia đình cho quản trị chọn (giống AdminFamilyPicker của web). */
  families: () => allPages<DashboardFamily>('/families', { sort: 'name,asc' }),
  exportMembersCsv: (params: { familyId?: string; branchId?: string }) => {
    const query = scopeQuery(params);
    return downloadAndOpen(
      `/dashboard/reports/members${query ? `?${query}` : ''}`,
      `thanh-vien-${Date.now()}.csv`,
      'text/csv',
      'Danh sách thành viên',
    );
  },
  exportPdf: (params: {
    reportType: DashboardReportType;
    familyId?: string;
    branchId?: string;
    from: string;
    to: string;
    title: string;
  }) => {
    const query = [`from=${enc(params.from)}`, `to=${enc(params.to)}`, scopeQuery(params)]
      .filter(Boolean)
      .join('&');
    return downloadAndOpen(
      `/dashboard/reports/${params.reportType}/pdf?${query}`,
      `bao-cao-${params.reportType.toLowerCase()}-${Date.now()}.pdf`,
      'application/pdf',
      params.title,
    );
  },
};

/** Chi trong phạm vi một chi gốc (trưởng chi chỉ thấy chi mình và chi con), như getManagedBranches của web. */
export function managedBranches<B extends { id?: string | null; parentBranchId?: string | null }>(
  branches: B[],
  rootId: string,
): B[] {
  const allowed = new Set([rootId]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const b of branches) {
      if (b.id && b.parentBranchId && allowed.has(b.parentBranchId) && !allowed.has(b.id)) {
        allowed.add(b.id);
        changed = true;
      }
    }
  }
  return branches.filter((b) => b.id && allowed.has(b.id));
}
