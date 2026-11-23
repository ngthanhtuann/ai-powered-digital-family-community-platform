'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { TextField } from '@/components/ui/TextField';
import { formatDate, formatDateTime } from '@/lib/datetime';
import { genealogyApi, type Person } from '@/services/genealogy';

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.');

/**
 * BR-GEN-24 (SCRUM-135) trên trang hồ sơ: con ruột gửi báo mất; người xác nhận thấy báo đang chờ và nút Xác nhận;
 * sau khi xác nhận mất trưởng họ/chi thì hiện gợi ý người kế nhiệm.
 */
export function DeathReportBox({ person }: { person: Person }) {
  const cache = useQueryClient();
  const [open, setOpen] = useState(false);
  const report = useQuery({
    queryKey: ['death-report', person.id],
    queryFn: () => genealogyApi.latestDeathReport(person.id!),
  });
  const refresh = () => Promise.all(['death-report', 'person', 'tree', 'members'].map((key) => cache.invalidateQueries({ queryKey: [key] })));
  const confirm = useMutation({
    mutationFn: (id: string) => genealogyApi.confirmDeathReport(id),
    onSuccess: refresh,
  });
  const r = report.data;
  if (report.isPending) return null;
  if (report.isError) return <p role="alert" className="text-body text-error">{errorText(report.error)}</p>;

  return (
    <>
      {r?.status === 'PENDING' && (
        <section role="status" className="space-y-2 rounded-card border border-warning bg-warning-soft p-4">
          <p className="text-label">
            {r.reportedByName ?? 'Một người con'} báo {r.personName} đã mất ngày {formatDate(r.deathDate!)}
            {r.deathTime ? ` lúc ${r.deathTime}` : ''}.
          </p>
          <p className="text-body text-text-secondary">
            Đang chờ xác nhận đến {formatDateTime(r.expiresAt!)}; quá hạn không ai xác nhận thì báo mất tự hủy.
          </p>
          {r.canConfirm && (
            <Button fullWidth={false} loading={confirm.isPending} onClick={() => confirm.mutate(r.id!)}>Xác nhận đã mất</Button>
          )}
          {confirm.isError && <p role="alert" className="text-body text-error">{errorText(confirm.error)}</p>}
        </section>
      )}
      {r?.status === 'CONFIRMED' && r.successorName && (
        <section className="space-y-1 rounded-card border border-border bg-surface p-4">
          <p className="text-label">Gợi ý người kế nhiệm: {r.successorName}</p>
          <p className="text-body text-text-secondary">
            Theo thứ tự con trai ruột lớn nhất còn sống có tài khoản, rồi cháu đích tôn. Trưởng họ quyết định ở{' '}
            <Link href="/family-roles" className="text-info underline">Phân công trưởng chi</Link>.
          </p>
        </section>
      )}
      {!r && person.canReportDeath && (
        <Button fullWidth={false} variant="secondary" onClick={() => setOpen(true)}>Báo mất</Button>
      )}
      {open && <ReportModal person={person} onClose={() => setOpen(false)} onDone={refresh} />}
    </>
  );
}

function ReportModal({ person, onClose, onDone }: { person: Person; onClose: () => void; onDone: () => Promise<unknown> }) {
  const [deathDate, setDeathDate] = useState('');
  const [deathTime, setDeathTime] = useState('');
  const [burialPlace, setBurialPlace] = useState('');
  const today = new Date().toLocaleDateString('sv-SE');
  const save = useMutation({
    mutationFn: () => genealogyApi.reportDeath(person.id!, {
      deathDate, deathTime: deathTime || null, burialPlace: burialPlace.trim() || null,
    }),
    onSuccess: async () => { await onDone(); onClose(); },
  });
  return (
    <Modal open title={`Báo mất ${person.fullName}`} onClose={onClose}>
      <form className="grid gap-4 p-5" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        <p className="text-body text-text-secondary">
          Báo mất cần một người xác nhận (con ruột khác, hoặc trưởng chi, trưởng họ) trong 7 ngày rồi mới chuyển hồ sơ sang đã mất.
        </p>
        <TextField label="Ngày mất" type="date" required max={today} value={deathDate} onChange={(e) => setDeathDate(e.target.value)} />
        <TextField label="Giờ mất (không bắt buộc)" type="time" value={deathTime} onChange={(e) => setDeathTime(e.target.value)} />
        <TextField label="Nơi an táng (không bắt buộc)" maxLength={255} value={burialPlace} onChange={(e) => setBurialPlace(e.target.value)} />
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending} disabled={!deathDate}>Gửi báo mất</Button>
      </form>
    </Modal>
  );
}
