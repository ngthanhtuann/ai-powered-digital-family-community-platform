'use client';

import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { Card } from '@/components/ui/Card';
import { Button } from '@/components/ui/Button';
import { datedFileName, saveBlob } from '@/lib/download';
import { getMyMembership, getMyProfile } from '@/services/auth';
import { dashboardApi, type DashboardBranch, type DashboardStatistics } from '@/services/dashboard';
import tailwindConfig from '../../../tailwind.config';

// Recharts cần mã màu dạng chuỗi nên đọc thẳng token trong tailwind.config (design-system.md mục 6).
const tokens = tailwindConfig.theme?.extend?.colors as Record<string, string>;
const chartColors = [
  tokens.primary,
  tokens.secondary,
  tokens.success,
  tokens.info,
  tokens.warning,
  tokens['text-secondary'],
];
const selectClass =
  'h-[42px] rounded-control border border-border-strong bg-surface px-3 text-body text-text-primary focus-visible:outline-2 focus-visible:outline-info';
const dateFormatter = new Intl.DateTimeFormat('vi-VN', {
  timeZone: 'Asia/Ho_Chi_Minh',
  day: '2-digit',
  month: '2-digit',
  year: 'numeric',
  hour: '2-digit',
  minute: '2-digit',
});

function getManagedBranches(branches: DashboardBranch[], rootId: string): DashboardBranch[] {
  const allowed = new Set([rootId]);
  let changed = true;
  while (changed) {
    changed = false;
    for (const branch of branches) {
      if (
        branch.id &&
        branch.parentBranchId &&
        allowed.has(branch.parentBranchId) &&
        !allowed.has(branch.id)
      ) {
        allowed.add(branch.id);
        changed = true;
      }
    }
  }
  return branches.filter((branch) => branch.id && allowed.has(branch.id));
}

function chartEmpty(data: DashboardStatistics | undefined, values: number[]) {
  return !data || values.every((value) => value === 0);
}

export default function DashboardPage() {
  const [selectedFamily, setSelectedFamily] = useState('');
  const [selectedBranch, setSelectedBranch] = useState('');
  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState('');
  const membership = useQuery({ queryKey: ['app-shell-membership'], queryFn: getMyMembership });
  const profile = useQuery({ queryKey: ['app-shell-profile'], queryFn: getMyProfile });
  const isSystemAdmin = profile.data?.roles.includes('SYSTEM_ADMIN') ?? false;
  const isBranchAdmin =
    membership.data?.status === 'APPROVED' && membership.data.role === 'BRANCH_ADMIN';
  const isDashboardAdmin = isSystemAdmin || isBranchAdmin;
  const families = useQuery({
    queryKey: ['dashboard-families'],
    queryFn: dashboardApi.families,
    enabled: isSystemAdmin,
  });
  const familyId = isSystemAdmin
    ? selectedFamily || families.data?.[0]?.id || ''
    : membership.data?.familyId || '';
  const branches = useQuery({
    queryKey: ['dashboard-branches', familyId],
    queryFn: () => dashboardApi.branches(familyId),
    enabled: isDashboardAdmin && Boolean(familyId),
  });
  const visibleBranches = useMemo(() => {
    const allBranches = branches.data ?? [];
    const managedBranchId = isBranchAdmin ? membership.data?.branchId : null;
    return managedBranchId ? getManagedBranches(allBranches, managedBranchId) : allBranches;
  }, [branches.data, isBranchAdmin, membership.data?.branchId]);
  const branchId =
    isBranchAdmin && membership.data?.branchId
      ? selectedBranch || membership.data.branchId
      : selectedBranch;
  const statistics = useQuery({
    queryKey: ['dashboard-statistics', familyId, branchId],
    queryFn: () =>
      dashboardApi.statistics({
        ...(isSystemAdmin ? { familyId } : {}),
        ...(branchId ? { branchId } : {}),
      }),
    enabled: isDashboardAdmin && Boolean(familyId),
  });

  if (membership.isPending || profile.isPending) {
    return <p className="text-body text-text-secondary">Đang kiểm tra quyền truy cập...</p>;
  }
  if (!isDashboardAdmin) {
    return (
      <p
        role="alert"
        className="rounded-card border border-error bg-error-soft p-4 text-body text-error"
      >
        Chỉ quản trị viên gia đình hoặc trưởng chi mới được xem dashboard.
      </p>
    );
  }

  const data = statistics.data;
  const genderData = data
    ? [
        { name: 'Nam', value: data.genderStats.MALE },
        { name: 'Nữ', value: data.genderStats.FEMALE },
        { name: 'Khác', value: data.genderStats.OTHER },
      ]
    : [];
  const errors = [families.error, branches.error, statistics.error].filter(
    (error) => error instanceof Error,
  );

  async function exportMembers() {
    setExporting(true);
    setExportError('');
    try {
      const blob = await dashboardApi.exportMembersCsv({
        ...(isSystemAdmin ? { familyId } : {}),
        ...(branchId ? { branchId } : {}),
      });
      saveBlob(blob, datedFileName('members', 'csv'));
    } catch (error) {
      setExportError(error instanceof Error ? error.message : 'Không xuất được CSV.');
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="space-y-5">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <p className="text-body text-text-secondary">
          Theo dõi thành viên, hoạt động cộng đồng và sự kiện trong phạm vi được quản lý.
        </p>
        <Button
          fullWidth={false}
          loading={exporting}
          disabled={!familyId}
          onClick={exportMembers}
        >
          Xuất CSV thành viên
        </Button>
      </div>

      {exportError && (
        <p role="alert" className="rounded-card border border-error bg-error-soft p-4 text-body text-error">
          Không xuất được CSV: {exportError}
        </p>
      )}

      <Card title="Bộ lọc thống kê">
        <div className="flex flex-col gap-4 sm:flex-row">
          {isSystemAdmin && (
            <label className="flex flex-1 flex-col gap-2 text-label">
              Gia đình
              <select
                className={selectClass}
                value={familyId}
                onChange={(event) => {
                  setSelectedFamily(event.target.value);
                  setSelectedBranch('');
                }}
              >
                {(families.data ?? []).map((family) => (
                  <option key={family.id} value={family.id}>
                    {family.name}
                  </option>
                ))}
              </select>
            </label>
          )}
          <label className="flex flex-1 flex-col gap-2 text-label">
            Chi họ
            <select
              className={selectClass}
              value={branchId}
              onChange={(event) => setSelectedBranch(event.target.value)}
              disabled={branches.isPending || !visibleBranches.length}
            >
              {(!isBranchAdmin || !membership.data?.branchId) && (
                <option value="">Toàn gia đình</option>
              )}
              {visibleBranches.map((branch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name}
                </option>
              ))}
            </select>
          </label>
        </div>
      </Card>

      {errors.map((error) => (
        <p
          key={error.message}
          role="alert"
          className="rounded-card border border-error bg-error-soft p-4 text-body text-error"
        >
          Không tải được dashboard: {error.message}
        </p>
      ))}
      {(families.isPending || statistics.isPending) && (
        <p className="text-body text-text-secondary">Đang tải số liệu...</p>
      )}

      {data && (
        <>
          <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
            <Card title="Tổng thành viên">
              <p className="text-3xl font-semibold text-primary">
                {data.totalMembers.toLocaleString('vi-VN')}
              </p>
              <p className="mt-1 text-caption text-text-secondary">Bao gồm thành viên đã mất</p>
            </Card>
            <Card title="Nam">
              <p className="text-3xl font-semibold text-primary">
                {data.genderStats.MALE.toLocaleString('vi-VN')}
              </p>
              <p className="mt-1 text-caption text-text-secondary">Thành viên trong phạm vi lọc</p>
            </Card>
            <Card title="Nữ">
              <p className="text-3xl font-semibold text-primary">
                {data.genderStats.FEMALE.toLocaleString('vi-VN')}
              </p>
              <p className="mt-1 text-caption text-text-secondary">Thành viên trong phạm vi lọc</p>
            </Card>
            <Card title="Sự kiện sắp tới">
              <p className="text-3xl font-semibold text-primary">{data.upcomingEvents.length}</p>
              <p className="mt-1 text-caption text-text-secondary">Sự kiện gần nhất</p>
            </Card>
          </div>

          <div className="grid gap-5 xl:grid-cols-2">
            <Card title="Thành viên theo thế hệ">
              {chartEmpty(
                data,
                data.generationStats.map((item) => item.count),
              ) ? (
                <p className="py-10 text-center text-body text-text-secondary">
                  Chưa có dữ liệu thế hệ.
                </p>
              ) : (
                <div className="h-72">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={data.generationStats}
                      margin={{ top: 8, right: 12, bottom: 8, left: 0 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis
                        dataKey="generation"
                        tickFormatter={(value: number) => `Đời ${value}`}
                      />
                      <YAxis allowDecimals={false} />
                      <Tooltip />
                      <Bar
                        dataKey="count"
                        name="Thành viên"
                        fill={chartColors[0]}
                        radius={[5, 5, 0, 0]}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>

            <Card title="Thành viên theo độ tuổi">
              {chartEmpty(
                data,
                data.ageStats.map((item) => item.count),
              ) ? (
                <p className="py-10 text-center text-body text-text-secondary">
                  Chưa có dữ liệu độ tuổi.
                </p>
              ) : (
                <div className="h-72">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={data.ageStats}
                      margin={{ top: 8, right: 12, bottom: 8, left: 0 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis dataKey="ageRange" />
                      <YAxis allowDecimals={false} />
                      <Tooltip />
                      <Bar
                        dataKey="count"
                        name="Thành viên"
                        fill={chartColors[4]}
                        radius={[5, 5, 0, 0]}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>

            <Card title="Cơ cấu giới tính">
              {chartEmpty(
                data,
                genderData.map((item) => item.value),
              ) ? (
                <p className="py-10 text-center text-body text-text-secondary">
                  Chưa có dữ liệu giới tính.
                </p>
              ) : (
                <div className="h-72">
                  <ResponsiveContainer width="100%" height="100%">
                    <PieChart>
                      <Pie data={genderData} dataKey="value" nameKey="name" outerRadius={90} label>
                        {genderData.map((item, index) => (
                          <Cell key={item.name} fill={chartColors[index]} />
                        ))}
                      </Pie>
                      <Tooltip />
                      <Legend />
                    </PieChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>

            <Card title="Nghề nghiệp" description="Không bao gồm hồ sơ của trẻ dưới 16 tuổi.">
              {chartEmpty(
                data,
                data.professionStats.map((item) => item.count),
              ) ? (
                <p className="py-10 text-center text-body text-text-secondary">
                  Chưa có hồ sơ nghề nghiệp.
                </p>
              ) : (
                <div className="h-72">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={data.professionStats.slice(0, 8)}
                      layout="vertical"
                      margin={{ left: 18 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis type="number" allowDecimals={false} />
                      <YAxis type="category" dataKey="jobTitle" width={110} />
                      <Tooltip />
                      <Bar
                        dataKey="count"
                        name="Thành viên"
                        fill={chartColors[1]}
                        radius={[0, 5, 5, 0]}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>

            <Card title="Nơi ở" description="Không bao gồm hồ sơ của trẻ dưới 16 tuổi.">
              {chartEmpty(
                data,
                data.locationStats.map((item) => item.count),
              ) ? (
                <p className="py-10 text-center text-body text-text-secondary">
                  Chưa có dữ liệu nơi ở.
                </p>
              ) : (
                <div className="h-72">
                  <ResponsiveContainer width="100%" height="100%">
                    <BarChart
                      data={data.locationStats.slice(0, 8)}
                      layout="vertical"
                      margin={{ left: 18 }}
                    >
                      <CartesianGrid strokeDasharray="3 3" />
                      <XAxis type="number" allowDecimals={false} />
                      <YAxis type="category" dataKey="location" width={110} />
                      <Tooltip />
                      <Bar
                        dataKey="count"
                        name="Thành viên"
                        fill={chartColors[2]}
                        radius={[0, 5, 5, 0]}
                      />
                    </BarChart>
                  </ResponsiveContainer>
                </div>
              )}
            </Card>

            <Card title="Bài đăng theo tuần" description="8 tuần gần nhất, theo múi giờ Việt Nam.">
              <div className="h-72">
                <ResponsiveContainer width="100%" height="100%">
                  <LineChart
                    data={data.weeklyPosts}
                    margin={{ top: 8, right: 12, bottom: 8, left: 0 }}
                  >
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis
                      dataKey="weekStart"
                      tickFormatter={(value: string) =>
                        new Intl.DateTimeFormat('vi-VN', {
                          timeZone: 'Asia/Ho_Chi_Minh',
                          day: '2-digit',
                          month: '2-digit',
                        }).format(new Date(value))
                      }
                    />
                    <YAxis allowDecimals={false} />
                    <Tooltip />
                    <Line
                      dataKey="postCount"
                      name="Bài đăng"
                      stroke={chartColors[3]}
                      strokeWidth={3}
                    />
                  </LineChart>
                </ResponsiveContainer>
              </div>
            </Card>

            <Card title="Sự kiện sắp tới">
              {data.upcomingEvents.length ? (
                <ul className="divide-y divide-border">
                  {data.upcomingEvents.map((event) => (
                    <li key={event.id} className="py-3 first:pt-0 last:pb-0">
                      <p className="font-semibold">{event.title}</p>
                      <p className="mt-1 text-caption text-text-secondary">
                        {dateFormatter.format(new Date(event.startAt))}
                      </p>
                    </li>
                  ))}
                </ul>
              ) : (
                <p className="py-10 text-center text-body text-text-secondary">
                  Chưa có sự kiện sắp tới.
                </p>
              )}
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
