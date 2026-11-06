import { api } from '@/lib/api';
import type { components } from '@/services/generated/heritage-directory-dashboard';
import type { components as GenealogyComponents } from '@/services/generated/genealogy';

type Schemas = components['schemas'];
type GenealogySchemas = GenealogyComponents['schemas'];

export type DashboardStatistics = Schemas['DashboardStatisticsData'];
export type DashboardFamily = GenealogySchemas['Family'];
export type DashboardBranch = GenealogySchemas['Branch'];

async function allPages<T>(url: string, params: Record<string, unknown> = {}): Promise<T[]> {
  const pageSize = 100;
  const items: T[] = [];
  let page = 0;
  let total = Number.POSITIVE_INFINITY;
  while (items.length < total) {
    const result = await api.list<T>(url, { ...params, page, size: pageSize });
    items.push(...result.items);
    total = result.meta.total;
    page += 1;
  }
  return items;
}

export const dashboardApi = {
  statistics: (params: { familyId?: string; branchId?: string }) =>
    api.get<DashboardStatistics>('/dashboard/statistics', params),
  families: () => allPages<DashboardFamily>('/families', { sort: 'name,asc' }),
  branches: (familyId: string) =>
    allPages<DashboardBranch>(`/families/${familyId}/branches`, { sort: 'name,asc' }),
};
