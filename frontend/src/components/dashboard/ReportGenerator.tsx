'use client';

import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { datedFileName, saveBlob } from '@/lib/download';
import { getMyMembership, getMyProfile } from '@/services/auth';
import {
  dashboardApi,
  getManagedBranches,
  type DashboardBranch,
  type DashboardReportType,
} from '@/services/dashboard';

const selectClass =
  'h-[42px] rounded-control border border-border-strong bg-surface px-3 text-body text-text-primary focus-visible:outline-2 focus-visible:outline-info';
const toDateInputValue = (date: Date) =>
  new Date(date.getTime() - date.getTimezoneOffset() * 60_000).toISOString().slice(0, 10);
const initialReportFrom = () => {
  const date = new Date();
  date.setDate(date.getDate() - 29);
  return toDateInputValue(date);
};

export function ReportGenerator() {
  const [selectedFamily, setSelectedFamily] = useState('');
  const [selectedBranch, setSelectedBranch] = useState('');
  const [reportType, setReportType] = useState<DashboardReportType>('GENEALOGY');
  const [reportFrom, setReportFrom] = useState(initialReportFrom);
  const [reportTo, setReportTo] = useState(() => toDateInputValue(new Date()));
  const [pdfAction, setPdfAction] = useState<'preview' | 'download' | null>(null);
  const [pdfPreviewUrl, setPdfPreviewUrl] = useState('');
  const [pdfError, setPdfError] = useState('');
  const membership = useQuery({ queryKey: ['app-shell-membership'], queryFn: getMyMembership });
  const profile = useQuery({ queryKey: ['app-shell-profile'], queryFn: getMyProfile });
  const isSystemAdmin = profile.data?.roles.includes('SYSTEM_ADMIN') ?? false;
  const isBranchAdmin =
    membership.data?.status === 'APPROVED' && membership.data.role === 'BRANCH_ADMIN';
  const isReportAdmin = isSystemAdmin || isBranchAdmin;
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
    enabled: isReportAdmin && Boolean(familyId),
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
  const errors = [families.error, branches.error].filter((error) => error instanceof Error);

  useEffect(() => {
    setPdfPreviewUrl('');
  }, [familyId, branchId, reportType, reportFrom, reportTo]);
  useEffect(
    () => () => {
      if (pdfPreviewUrl) URL.revokeObjectURL(pdfPreviewUrl);
    },
    [pdfPreviewUrl],
  );

  if (membership.isPending || profile.isPending) {
    return <p className="text-body text-text-secondary">Đang kiểm tra quyền truy cập...</p>;
  }
  if (!isReportAdmin) {
    return (
      <p
        role="alert"
        className="rounded-card border border-error bg-error-soft p-4 text-body text-error"
      >
        Chỉ quản trị viên gia đình hoặc trưởng chi mới được tạo báo cáo.
      </p>
    );
  }

  async function handlePdf(action: 'preview' | 'download') {
    setPdfAction(action);
    setPdfError('');
    try {
      const blob = await dashboardApi.exportPdf({
        reportType,
        ...(isSystemAdmin ? { familyId } : {}),
        ...(branchId ? { branchId } : {}),
        from: reportFrom,
        to: reportTo,
      });
      if (action === 'preview') {
        setPdfPreviewUrl(URL.createObjectURL(blob));
      } else {
        saveBlob(blob, datedFileName(`report-${reportType.toLowerCase()}`, 'pdf'));
      }
    } catch (error) {
      setPdfError(error instanceof Error ? error.message : 'Không xuất được PDF.');
    } finally {
      setPdfAction(null);
    }
  }

  // BR-DSH-03: khoảng ngày tối đa 366 ngày, giống kiểm tra ở backend.
  const invalidReportRange =
    !reportFrom ||
    !reportTo ||
    reportFrom > reportTo ||
    (Date.parse(reportTo) - Date.parse(reportFrom)) / 86_400_000 > 365;

  return (
    <main className="mx-auto max-w-5xl space-y-5 px-4 py-8">
      <header>
        <h1 className="text-page-title">Báo cáo</h1>
        <p className="mt-1 text-body text-text-secondary">
          Tạo, xem trước và tải báo cáo PDF theo gia đình, chi họ và khoảng thời gian.
        </p>
      </header>
      <Card title="Phạm vi và bộ lọc báo cáo">
        <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
          {isSystemAdmin && (
            <label className="flex flex-col gap-2 text-label">
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
          <label className="flex flex-col gap-2 text-label">
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
              {visibleBranches.map((branch: DashboardBranch) => (
                <option key={branch.id} value={branch.id}>
                  {branch.name}
                </option>
              ))}
            </select>
          </label>
          <label className="flex flex-col gap-2 text-label">
            Loại báo cáo
            <select
              className={selectClass}
              value={reportType}
              onChange={(event) => setReportType(event.target.value as DashboardReportType)}
            >
              <option value="GENEALOGY">Gia phả</option>
              <option value="COMMUNITY">Hoạt động cộng đồng</option>
              <option value="EVENTS">Sự kiện</option>
            </select>
          </label>
          <label className="flex flex-col gap-2 text-label">
            Từ ngày
            <input
              className={selectClass}
              type="date"
              value={reportFrom}
              max={reportTo || undefined}
              onChange={(event) => setReportFrom(event.target.value)}
            />
          </label>
          <label className="flex flex-col gap-2 text-label">
            Đến ngày
            <input
              className={selectClass}
              type="date"
              value={reportTo}
              min={reportFrom || undefined}
              onChange={(event) => setReportTo(event.target.value)}
            />
          </label>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          <Button
            fullWidth={false}
            variant="secondary"
            loading={pdfAction === 'preview'}
            disabled={!familyId || invalidReportRange || pdfAction !== null}
            onClick={() => handlePdf('preview')}
          >
            Xem trước PDF
          </Button>
          <Button
            fullWidth={false}
            loading={pdfAction === 'download'}
            disabled={!familyId || invalidReportRange || pdfAction !== null}
            onClick={() => handlePdf('download')}
          >
            Tải PDF
          </Button>
        </div>
        {invalidReportRange && (
          <p role="alert" className="mt-3 text-caption text-error">
            Vui lòng chọn khoảng ngày hợp lệ, tối đa 366 ngày.
          </p>
        )}
        {pdfError && (
          <p
            role="alert"
            className="mt-3 rounded-card border border-error bg-error-soft p-4 text-body text-error"
          >
            Không xuất được PDF: {pdfError}
          </p>
        )}
        {errors.map((error) => (
          <p
            key={error.message}
            role="alert"
            className="mt-3 rounded-card border border-error bg-error-soft p-4 text-body text-error"
          >
            Không tải được dữ liệu phạm vi: {error.message}
          </p>
        ))}
        {((isSystemAdmin && families.isPending) || (Boolean(familyId) && branches.isPending)) && (
          <p className="mt-3 text-body text-text-secondary">Đang tải phạm vi...</p>
        )}
      </Card>
      {pdfPreviewUrl && (
        <Card title="Xem trước báo cáo">
          <iframe
            className="h-[70vh] w-full rounded-control border border-border"
            src={pdfPreviewUrl}
            title="Xem trước báo cáo PDF"
          />
        </Card>
      )}
    </main>
  );
}
