'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { TextField } from '@/components/ui/TextField';
import { Avatar } from '@/components/genealogy/PersonNode';
import { RelationModal, errorText, selectClass, yearOf, type RelationMode } from '@/components/genealogy/RelationModal';
import { genealogyApi, type DeathInput, type Person, type Relationship, type TreeEdge, type TreeNode } from '@/services/genealogy';
import { aiApi } from '@/services/ai';

const GENDER_LABEL: Record<string, string> = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

/**
 * Panel chi tiết khi bấm một người trên cây (SCRUM-52 bước 4): thông tin chính, nút thêm cha/mẹ/con/vợ chồng
 * cho người quản lý, và tra "A là gì của B" để tô sáng đường quan hệ (UC-GEN-07, gọi UC-GEN-08).
 * SCRUM-117: chọn người để tra ngay trong danh sách người trên cây (cây đã mở từ người tổ), bỏ ô tìm riêng.
 */
/** Vợ/chồng của một người theo cạnh MARRIAGE trên cây (SCRUM-144, 145). */
export type SpouseLink = { marriageId: string; id: string; name: string; status: string; deceased: boolean };

export function PersonPanel({ familyId, personId, people, edges = [], myPersonId, canManage, onRelation, onClose }: {
  familyId: string;
  personId: string;
  people: TreeNode[];
  edges?: TreeEdge[];
  myPersonId?: string;
  canManage: boolean;
  onRelation: (relation: Relationship | null) => void;
  onClose: () => void;
}) {
  const person = useQuery({ queryKey: ['person', personId], queryFn: () => genealogyApi.person(personId) });
  const [mode, setMode] = useState<RelationMode | null>(null);
  const [deathOpen, setDeathOpen] = useState(false);
  const [adding, setAdding] = useState(false);
  const [divorcing, setDivorcing] = useState<SpouseLink | null>(null);
  const byId = new Map(people.map((n) => [n.id ?? '', n]));
  // BR-GEN-27: đã có cả cha và mẹ thì ẩn "Thêm cha/mẹ".
  const parentGenders = edges.filter((e) => e.type === 'PARENT_CHILD' && e.target === personId)
    .map((e) => byId.get(e.source ?? '')?.gender);
  const hasBothParents = parentGenders.includes('MALE') && parentGenders.includes('FEMALE');
  const spouses: SpouseLink[] = edges.filter((e) => e.type === 'MARRIAGE' && (e.source === personId || e.target === personId))
    .map((e) => {
      const other = byId.get((e.source === personId ? e.target : e.source) ?? '');
      return { marriageId: e.id ?? '', id: other?.id ?? '', name: other?.fullName ?? '', status: e.status ?? '', deceased: Boolean(other?.isDeceased) };
    })
    .filter((s) => s.id);
  const [replaceOpen, setReplaceOpen] = useState(false);
  const [otherId, setOtherId] = useState('');
  const explanation = useMutation({ mutationFn: () => aiApi.explainRelationship(personId, otherId) });
  const relation = useMutation({
    mutationFn: () => genealogyApi.relationship(personId, otherId),
    onMutate: () => explanation.reset(), // tra cặp khác thì bỏ lời giải thích AI của cặp cũ
    onSuccess: (data) => onRelation(data),
  });
  const p = person.data;
  const others = people.filter((n) => n.id !== personId).sort((a, b) => (a.fullName ?? '').localeCompare(b.fullName ?? '', 'vi'));
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
            {p.isDeceased && p.deathDate && (
              <p className="text-caption text-text-secondary">
                Mất ngày {p.deathDate.split('-').reverse().join('/')}{p.deathTime ? ` lúc ${p.deathTime}` : ''}
              </p>
            )}
          </div>
        </div>
        <Link href={`/persons/${p.id}`} className="inline-block text-body text-info underline">Xem hồ sơ đầy đủ</Link>
        {canManage && (
          <div className="grid gap-2">
            <Button variant="secondary" aria-expanded={adding} onClick={() => setAdding(!adding)}>Thêm người thân</Button>
            {adding && (
              <div className={`grid gap-2 ${hasBothParents ? 'grid-cols-2' : 'grid-cols-3'}`} role="group"
                aria-label={`Thêm người thân cho ${p.fullName}`}>
                <Button variant="secondary" onClick={() => setMode('child')}>Con</Button>
                {!hasBothParents && <Button variant="secondary" onClick={() => setMode('parent')}>Cha/mẹ</Button>}
                <Button variant="secondary" onClick={() => setMode('spouse')}>Vợ/chồng</Button>
              </div>
            )}
          </div>
        )}
        {p.canManage && (
          <Button variant="secondary" onClick={() => setDeathOpen(true)}>
            {p.isDeceased ? 'Sửa thông tin mất / báo nhầm' : 'Báo mất'}
          </Button>
        )}
        {p.canManage && p.userId && !p.isDeceased && (
          <Button variant="secondary" onClick={() => setReplaceOpen(true)}>Thay tài khoản (mất tài khoản)</Button>
        )}
        <form className="grid gap-2 border-t border-border pt-4" onSubmit={(e) => { e.preventDefault(); relation.mutate(); }}>
          <label className="grid gap-1 text-label">{p.fullName} là gì của
            <select className={selectClass} value={otherId} onChange={(e) => setOtherId(e.target.value)}>
              <option value="">{others.length ? 'Chọn người trên cây' : 'Chưa có ai khác trên cây'}</option>
              {others.map((n) => <option key={n.id} value={n.id}>{n.fullName}{yearOf(n) ? ` (${yearOf(n)})` : ''}</option>)}
            </select>
          </label>
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
      {spouses.length > 0 && p && (
        <div className="grid gap-2 border-t border-border pt-4">
          <p className="text-label">Hôn nhân</p>
          {spouses.map((s) => (
            <div key={s.marriageId} className="flex flex-wrap items-center justify-between gap-2 text-body">
              <span>{s.name} · {s.status === 'MARRIED' ? 'đang kết hôn' : s.status === 'DIVORCED' ? 'đã ly hôn' : 'góa'}</span>
              {/* BR-GEN-28: trưởng họ, trưởng chi hoặc chính hai vợ chồng ghi ly hôn. */}
              {s.status === 'MARRIED' && !s.deceased && (canManage || personId === myPersonId || s.id === myPersonId) && (
                <Button fullWidth={false} variant="secondary" onClick={() => setDivorcing(s)}>Ghi ly hôn</Button>
              )}
            </div>
          ))}
        </div>
      )}
      {mode && p && <RelationModal familyId={familyId} person={p} mode={mode} spouses={spouses} onClose={() => setMode(null)} />}
      {divorcing && p && <DivorceModal person={p} spouse={divorcing} onClose={() => setDivorcing(null)} />}
      {deathOpen && p && <DeathModal person={p} onClose={() => setDeathOpen(false)} />}
      {replaceOpen && p && <ReplaceAccountModal person={p} onClose={() => setReplaceOpen(false)} />}
    </aside>
  );
}

/**
 * BR-GEN-21 (SCRUM-134): người quản lý chi báo mất trực tiếp (kể cả hồ sơ đã gắn tài khoản), sửa ngày giờ mất,
 * hoặc khôi phục còn sống khi báo nhầm. Máy chủ khóa tài khoản và gửi thông báo tang.
 */
function DeathModal({ person, onClose }: { person: Person; onClose: () => void }) {
  const cache = useQueryClient();
  const [deathDate, setDeathDate] = useState(person.deathDate ?? '');
  const [deathTime, setDeathTime] = useState(person.deathTime ?? '');
  const [burialPlace, setBurialPlace] = useState(person.burialPlace ?? '');
  const [mistake, setMistake] = useState(false);
  const today = new Date().toLocaleDateString('sv-SE'); // YYYY-MM-DD theo giờ máy
  const save = useMutation({
    mutationFn: (input: DeathInput) => genealogyApi.setDeath(person.id!, input),
    onSuccess: async () => {
      await Promise.all(['person', 'tree', 'members'].map((key) => cache.invalidateQueries({ queryKey: [key] })));
      onClose();
    },
  });
  return (
    <Modal open title={person.isDeceased ? `Thông tin mất của ${person.fullName}` : `Báo mất ${person.fullName}`} onClose={onClose}>
      <form className="grid gap-4 p-5" onSubmit={(e) => {
        e.preventDefault();
        save.mutate({ isDeceased: true, deathDate, deathTime: deathTime || null, burialPlace: burialPlace.trim() || null });
      }}>
        {!person.isDeceased && (
          <p className="text-body text-text-secondary">
            Hồ sơ chuyển sang đã mất. Tài khoản gắn với hồ sơ (nếu có) bị khóa; người trong chi và trưởng họ nhận thông báo tang.
          </p>
        )}
        <TextField label="Ngày mất" type="date" required max={today} value={deathDate} onChange={(e) => setDeathDate(e.target.value)} />
        <TextField label="Giờ mất (không bắt buộc)" type="time" value={deathTime} onChange={(e) => setDeathTime(e.target.value)} />
        <TextField label="Nơi an táng (không bắt buộc)" maxLength={255} value={burialPlace}
          onChange={(e) => setBurialPlace(e.target.value)} />
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending} disabled={!deathDate}>
          {person.isDeceased ? 'Lưu' : 'Xác nhận đã mất'}
        </Button>
        {person.isDeceased && (
          <div className="grid gap-2 border-t border-border pt-4">
            <label className="flex min-h-11 items-center gap-2 rounded-control bg-warning-soft p-3 text-body">
              <input type="checkbox" className="h-5 w-5" checked={mistake} onChange={(e) => setMistake(e.target.checked)} />
              Báo nhầm: người này vẫn còn sống
            </label>
            <Button type="button" variant="secondary" disabled={!mistake} loading={save.isPending}
              onClick={() => save.mutate({ isDeceased: false })}>
              Khôi phục còn sống
            </Button>
          </div>
        )}
      </form>
    </Modal>
  );
}

/**
 * BR-GEN-23 (SCRUM-140): người thân mất tài khoản đăng ký tài khoản mới và được duyệt vào dòng họ, rồi người quản lý gắn tài khoản
 * mới vào hồ sơ. Tài khoản cũ bị khóa (không xóa); vai trò và nội dung công khai chuyển sang tài khoản mới.
 */
function ReplaceAccountModal({ person, onClose }: { person: Person; onClose: () => void }) {
  const cache = useQueryClient();
  const [newUserId, setNewUserId] = useState('');
  const [reason, setReason] = useState('');
  const candidates = useQuery({
    queryKey: ['account-candidates', person.id],
    queryFn: () => genealogyApi.accountCandidates(person.id!),
  });
  const save = useMutation({
    mutationFn: () => genealogyApi.replaceAccount(person.id!, { newUserId, reason: reason.trim() }),
    onSuccess: async () => {
      await Promise.all(['person', 'tree', 'members', 'family-accounts'].map((key) => cache.invalidateQueries({ queryKey: [key] })));
      onClose();
    },
  });
  return (
    <Modal open title={`Thay tài khoản cho ${person.fullName}`} onClose={onClose}>
      <form className="grid gap-4 p-5" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        <p className="text-body text-text-secondary">
          Dùng khi người này mất tài khoản cũ. Trước tiên họ đăng ký tài khoản mới và được duyệt vào dòng họ. Tài khoản cũ sẽ bị khóa
          (không xóa); vai trò trưởng họ/chi, bài đăng, bình luận, sự kiện, tài liệu chuyển sang tài khoản mới.
        </p>
        {candidates.isPending && <p className="text-body text-text-secondary">Đang tải danh sách tài khoản...</p>}
        {candidates.isError && <p role="alert" className="text-body text-error">{errorText(candidates.error)}</p>}
        <label className="grid gap-1 text-label">Tài khoản mới
          <select className={selectClass} value={newUserId} required onChange={(e) => setNewUserId(e.target.value)}>
            <option value="">{candidates.data?.length ? 'Chọn tài khoản' : 'Chưa có tài khoản nào chưa gắn hồ sơ'}</option>
            {candidates.data?.map((c) => <option key={c.userId} value={c.userId}>{c.fullName}</option>)}
          </select>
        </label>
        <TextField label="Lý do" required maxLength={255} value={reason} onChange={(e) => setReason(e.target.value)}
          placeholder="Ví dụ: mất điện thoại và email cũ" />
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending} disabled={!newUserId || !reason.trim()}>Thay tài khoản</Button>
      </form>
    </Modal>
  );
}

/** BR-GEN-28 (SCRUM-145): ghi đã ly hôn; vợ/chồng ngoài tộc sẽ mờ trên cây và bị ẩn với thành viên. */
function DivorceModal({ person, spouse, onClose }: { person: Person; spouse: SpouseLink; onClose: () => void }) {
  const cache = useQueryClient();
  const today = new Date().toLocaleDateString('sv-SE');
  const [endDate, setEndDate] = useState(today);
  const [sure, setSure] = useState(false);
  const save = useMutation({
    mutationFn: () => genealogyApi.updateMarriage(spouse.marriageId, { status: 'DIVORCED', endDate }),
    onSuccess: async () => {
      await Promise.all(['person', 'tree', 'members', 'posts'].map((key) => cache.invalidateQueries({ queryKey: [key] })));
      onClose();
    },
  });
  return (
    <Modal open title={`Ghi ly hôn: ${person.fullName} và ${spouse.name}`} onClose={onClose}>
      <form className="grid gap-4 p-5" onSubmit={(e) => { e.preventDefault(); save.mutate(); }}>
        <p className="text-body text-text-secondary">
          Người ngoài tộc sau khi ly hôn sẽ mờ trên cây, thành viên không mở được hồ sơ và bài viết của họ; tài khoản của họ (nếu có)
          rời dòng họ. Trưởng họ, trưởng chi vẫn xem được.
        </p>
        <TextField label="Ngày ly hôn" type="date" required max={today} value={endDate} onChange={(e) => setEndDate(e.target.value)} />
        <label className="flex min-h-11 items-center gap-2 rounded-control bg-warning-soft p-3 text-body">
          <input type="checkbox" className="h-5 w-5" checked={sure} onChange={(e) => setSure(e.target.checked)} />
          Tôi xác nhận hai người đã ly hôn
        </label>
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending} disabled={!sure || !endDate}>Ghi ly hôn</Button>
      </form>
    </Modal>
  );
}
