'use client';

import Link from 'next/link';
import { useState } from 'react';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { Avatar } from '@/components/genealogy/PersonNode';
import { RelationModal, errorText, selectClass, yearOf, type RelationMode } from '@/components/genealogy/RelationModal';
import { genealogyApi, type Relationship, type TreeNode } from '@/services/genealogy';
import { aiApi } from '@/services/ai';

const GENDER_LABEL: Record<string, string> = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

/**
 * Panel chi tiết khi bấm một người trên cây (SCRUM-52 bước 4): thông tin chính, nút thêm cha/mẹ/con/vợ chồng
 * cho người quản lý, và tra "A là gì của B" để tô sáng đường quan hệ (UC-GEN-07, gọi UC-GEN-08).
 * SCRUM-56: người để tra chọn trong cả dòng họ (không chỉ người đang hiện trên cây).
 */
export function PersonPanel({ familyId, personId, people, canManage, onRelation, onClose }: {
  familyId: string;
  personId: string;
  people: TreeNode[];
  canManage: boolean;
  onRelation: (relation: Relationship | null) => void;
  onClose: () => void;
}) {
  const person = useQuery({ queryKey: ['person', personId], queryFn: () => genealogyApi.person(personId) });
  const [mode, setMode] = useState<RelationMode | null>(null);
  const [otherId, setOtherId] = useState('');
  const explanation = useMutation({ mutationFn: () => aiApi.explainRelationship(personId, otherId) });
  const [q, setQ] = useState('');
  const candidates = useQuery({
    queryKey: ['persons-pick', familyId, q.trim()],
    queryFn: () => genealogyApi.persons(familyId, { q: q.trim() || undefined, page: 0, size: 100, sort: 'fullName,asc' }),
    placeholderData: keepPreviousData, // gõ tên thì giữ danh sách cũ tới khi có kết quả mới, ô chọn không nháy
  });
  const relation = useMutation({
    mutationFn: () => genealogyApi.relationship(personId, otherId),
    onMutate: () => explanation.reset(), // tra cặp khác thì bỏ lời giải thích AI của cặp cũ
    onSuccess: (data) => onRelation(data),
  });
  const p = person.data;
  const others = candidates.data?.items.filter((n) => n.id !== personId) ?? [];
  const more = (candidates.data?.meta.total ?? 0) > (candidates.data?.items.length ?? 0);
  const shownOnTree = new Set(people.map((n) => n.id));
  const offTree = relation.data?.path?.filter((step) => !shownOnTree.has(step.id)).length ?? 0;

  return (
    <aside aria-label="Chi tiết thành viên" className="space-y-4 rounded-card border border-border bg-surface p-4 shadow-card">
      <div className="flex items-start justify-between gap-2">
        <h2 className="text-section-title">Chi tiết</h2>
        <Button fullWidth={false} variant="secondary" onClick={onClose}>Đóng</Button>
      </div>
      {person.isPending && <p className="text-body text-text-secondary">Đang tải...</p>}
      {person.isError && <p role="alert" className="text-body text-error">{errorText(person.error)}</p>}
      {p && <>
        <div className="flex items-center gap-3">
          <Avatar mediaId={p.avatarMediaId} name={p.fullName ?? ''} size="h-14 w-14" />
          <div className="min-w-0">
            <p className="truncate text-label">{p.fullName}</p>
            <p className="text-caption text-text-secondary">
              {[p.gender && GENDER_LABEL[p.gender], yearOf(p) && `Sinh ${yearOf(p)}`, p.isDeceased && 'Đã mất']
                .filter(Boolean).join(' · ') || 'Chưa có thông tin'}
            </p>
            {p.currentProvince && <p className="text-caption text-text-secondary">{p.currentProvince}</p>}
          </div>
        </div>
        <Link href={`/persons/${p.id}`} className="inline-block text-body text-info underline">Xem hồ sơ đầy đủ</Link>
        {canManage && (
          <div className="grid gap-2">
            <Button variant="secondary" onClick={() => setMode('parent')}>Thêm cha/mẹ</Button>
            <Button variant="secondary" onClick={() => setMode('child')}>Thêm con</Button>
            <Button variant="secondary" onClick={() => setMode('spouse')}>Thêm vợ/chồng</Button>
          </div>
        )}
        <form className="grid gap-2 border-t border-border pt-4" onSubmit={(e) => { e.preventDefault(); relation.mutate(); }}>
          <TextField label="Tìm người trong dòng họ" maxLength={100} value={q} placeholder="Gõ tên để lọc"
            onChange={(e) => { setQ(e.target.value); setOtherId(''); }} />
          <label className="grid gap-1 text-label">{p.fullName} là gì của
            <select className={selectClass} value={otherId} onChange={(e) => setOtherId(e.target.value)}
              disabled={!candidates.data}>
              <option value="">{!candidates.data ? 'Đang tải...' : others.length ? 'Chọn người' : 'Không tìm thấy ai'}</option>
              {others.map((n) => <option key={n.id} value={n.id}>{n.fullName}{yearOf(n) ? ` (${yearOf(n)})` : ''}</option>)}
            </select>
          </label>
          {more && <p className="text-caption text-text-secondary">Chỉ hiện 100 người đầu, gõ tên để tìm người khác.</p>}
          {candidates.isError && <p role="alert" className="text-body text-error">{errorText(candidates.error)}</p>}
          <Button type="submit" loading={relation.isPending} disabled={!otherId}>Tra quan hệ</Button>
          {relation.isError && <p role="alert" className="text-body text-error">{errorText(relation.error)}</p>}
          {relation.data && (
            <div role="status" className="space-y-1 rounded-control bg-info-soft p-3 text-body">
              <p>{relation.data.description}</p>
              <Button fullWidth={false} variant="secondary" loading={explanation.isPending}
                onClick={() => explanation.mutate()} disabled={!relation.data.supported}>
                Giải thích bằng AI
              </Button>
              {explanation.isError && <p role="alert" className="text-body text-error">{errorText(explanation.error)}</p>}
              {explanation.data && <p className="text-body">{explanation.data.explanation}</p>}
              {/* Có tổ tiên chung thì trang cây tự tải thêm; quan hệ chỉ qua hôn nhân thì không có gốc để tải. */}
              {offTree > 0 && (
                <p className="text-caption">
                  {offTree} người trên đường đi chưa hiện trên cây
                  {relation.data.commonAncestorId ? ', đang tải thêm...' : ' (mở thêm nhánh để thấy).'}
                </p>
              )}
              <Button fullWidth={false} variant="secondary" onClick={() => { relation.reset(); explanation.reset(); onRelation(null); }}>Bỏ tô sáng</Button>
            </div>
          )}
        </form>
      </>}
      {mode && p && <RelationModal familyId={familyId} person={p} mode={mode} onClose={() => setMode(null)} />}
    </aside>
  );
}
