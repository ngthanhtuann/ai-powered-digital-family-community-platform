'use client';

import Link from 'next/link';
import { useState, type FormEvent } from 'react';
import { keepPreviousData, useMutation, useQuery } from '@tanstack/react-query';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { errorText, selectClass, yearOf } from '@/components/genealogy/RelationModal';
import { Button } from '@/components/ui/Button';
import { aiApi } from '@/services/ai';
import { genealogyApi, type Relationship } from '@/services/genealogy';
import { AdminFamilyPicker, useAdminFamily } from '@/components/admin/AdminFamilyPicker';

type Step = NonNullable<Relationship['path']>[number];

/**
 * Mục "Tra cứu xưng hô" (UC-GEN-07, gọi UC-GEN-08; Figma 03 Gia phả, khung "UC-GEN-07, 08 Tra quan hệ"):
 * chọn hai người trong dòng họ, xem A là gì của B, đường đi qua tổ tiên chung và mở cây có tô sáng đường đi.
 */
export default function RelationshipPage() {
  const access = useHeritageAccess();
  const adminFamily = useAdminFamily() ?? ''; // gia đình chung chọn ở header (SCRUM-118)
  const familyId = access.familyId ?? (access.isSystemAdmin ? adminFamily.trim() : '');
  const [personA, setPersonA] = useState('');
  const [personB, setPersonB] = useState('');
  const relation = useMutation({ mutationFn: () => genealogyApi.relationship(personA, personB) });
  const explanation = useMutation({ mutationFn: () => aiApi.explainRelationship(personA, personB) });

  function lookup(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    explanation.reset();
    relation.mutate();
  }

  function pick(set: (id: string) => void) {
    return (id: string) => { set(id); relation.reset(); explanation.reset(); };
  }

  function swap() {
    setPersonA(personB);
    setPersonB(personA);
    relation.reset();
    explanation.reset();
  }

  const result = relation.data;
  // Kết quả ứng với cặp đang chọn; đổi người thì nút "Xem trên cây" vẫn dẫn đúng cặp đã tra.
  const treeHref = result && `/families/${familyId}/tree?a=${personA}&b=${personB}`;

  return (
    <main className="mx-auto max-w-5xl space-y-6 px-4 py-8 md:px-6">
      <header>
        <h1 className="text-page-title">Tra quan hệ</h1>
        <p className="mt-1 text-body text-text-secondary">Chọn hai người để xem họ là gì của nhau.</p>
      </header>

      <AccessState loading={access.membership.isLoading || access.profile.isLoading}
        error={(access.membership.error as Error | null)?.message} />
      {access.isReady && !access.familyId && access.isSystemAdmin && (
        <AdminFamilyPicker />
      )}

      {familyId && (
        <form onSubmit={lookup} className="space-y-4 rounded-card border border-border bg-surface p-4 shadow-card md:p-5">
          <div className="grid gap-3 md:grid-cols-[1fr_auto_1fr] md:items-start">
            <PersonPicker label="Người A" familyId={familyId} value={personA} exclude={personB} onChange={pick(setPersonA)} />
            <button type="button" onClick={swap} aria-label="Đổi chỗ người A và người B"
              disabled={!personA && !personB}
              className="h-11 w-11 justify-self-center rounded-control border border-border-strong bg-surface text-section-title text-text-primary hover:bg-surface-muted disabled:opacity-50 md:mt-[26px]">
              ⇄
            </button>
            <PersonPicker label="Người B" familyId={familyId} value={personB} exclude={personA} onChange={pick(setPersonB)} />
          </div>
          <div className="md:w-[180px]">
            <Button type="submit" loading={relation.isPending} disabled={!personA || !personB}>Tra quan hệ</Button>
          </div>
          {relation.isError && <p role="alert" className="text-body text-error">{errorText(relation.error)}</p>}
        </form>
      )}

      {result && (
        <section aria-label="Kết quả" role="status"
          className="space-y-3 rounded-card border border-border bg-surface p-4 shadow-card md:p-5">
          <p className="text-caption font-semibold text-text-secondary">Kết quả</p>
          <h2 className="text-page-title">{result.description}</h2>
          {result.supported ? (
            <>
              <span className="inline-block rounded-control bg-success-soft px-2 py-0.5 text-caption text-success">
                Có quan hệ huyết thống
              </span>
              <p className="text-body text-text-secondary">
                {ancestorName(result) && `Tổ tiên chung: ${ancestorName(result)} · `}
                Số bước: {result.stepsFromA ?? 0} (từ A), {result.stepsFromB ?? 0} (từ B)
              </p>
              <PathChips path={result.path ?? []} />
              <div className="flex flex-wrap gap-2 pt-1">
                {treeHref && (
                  <Link href={treeHref}
                    className="inline-flex h-11 items-center rounded-control border border-border-strong px-4 text-label hover:bg-surface-muted">
                    Xem trên cây
                  </Link>
                )}
                <Button fullWidth={false} variant="secondary" loading={explanation.isPending} onClick={() => explanation.mutate()}>
                  Giải thích bằng AI
                </Button>
              </div>
              {explanation.isError && <p role="alert" className="text-body text-error">{errorText(explanation.error)}</p>}
              {explanation.data && <p className="rounded-control bg-info-soft p-3 text-body">{explanation.data.explanation}</p>}
              <MiniTree result={result} />
            </>
          ) : (
            <span className="inline-block rounded-control bg-warning-soft px-2 py-0.5 text-caption text-warning">
              Chưa xác định được quan hệ huyết thống
            </span>
          )}
        </section>
      )}
    </main>
  );
}

function ancestorName(result: Relationship) {
  return result.path?.find((step) => step.id === result.commonAncestorId)?.fullName;
}

/** Chọn người trong cả dòng họ: gõ tên để lọc (tìm không dấu, SCRUM-102), API trả tối đa 100 người mỗi lần. */
function PersonPicker({ label, familyId, value, exclude, onChange }: {
  label: string;
  familyId: string;
  value: string;
  exclude: string;
  onChange: (id: string) => void;
}) {
  const [q, setQ] = useState('');
  const people = useQuery({
    queryKey: ['persons-pick', familyId, q.trim()],
    queryFn: () => genealogyApi.persons(familyId, { q: q.trim() || undefined, page: 0, size: 100, sort: 'fullName,asc' }),
    placeholderData: keepPreviousData,
  });
  const options = people.data?.items.filter((p) => p.id !== exclude) ?? [];
  const more = (people.data?.meta.total ?? 0) > (people.data?.items.length ?? 0);
  // Người đã chọn có thể không còn trong kết quả lọc mới; vẫn giữ trong danh sách để ô chọn không trống.
  const chosen = useQuery({
    queryKey: ['person', value],
    queryFn: () => genealogyApi.person(value),
    enabled: Boolean(value) && !options.some((p) => p.id === value),
  });

  return (
    <div className="grid gap-2">
      <label className="grid gap-1 text-label">{label}
        <select className={selectClass} value={value} disabled={!people.data} onChange={(e) => onChange(e.target.value)}>
          <option value="">{!people.data ? 'Đang tải...' : options.length ? 'Chọn người' : 'Không tìm thấy ai'}</option>
          {chosen.data && !options.some((p) => p.id === value) && (
            <option value={value}>{chosen.data.fullName}</option>
          )}
          {options.map((p) => <option key={p.id} value={p.id}>{p.fullName}{yearOf(p) ? ` (${yearOf(p)})` : ''}</option>)}
        </select>
      </label>
      <input aria-label={`Lọc ${label} theo tên`} maxLength={100} value={q} placeholder="Gõ tên để lọc"
        onChange={(e) => setQ(e.target.value)}
        className="h-[42px] w-full rounded-control border border-border bg-surface px-3 text-body" />
      {more && <p className="text-caption text-text-secondary">Chỉ hiện 100 người đầu, gõ tên để tìm người khác.</p>}
      {people.isError && <p role="alert" className="text-body text-error">{errorText(people.error)}</p>}
    </div>
  );
}

function PersonChip({ name, onPath = true }: { name?: string; onPath?: boolean }) {
  return (
    <span className={`inline-flex min-h-8 items-center rounded-control border px-3 text-label ${
      onPath ? 'border-info bg-secondary text-text-primary' : 'border-border bg-surface'}`}>
      {name ?? 'Không rõ tên'}
    </span>
  );
}

/** Đường đi A → tổ tiên chung → B (chỉ tên, không ngày sinh hay địa chỉ, BR-GEN-10). */
function PathChips({ path }: { path: Step[] }) {
  return (
    <ol aria-label="Đường quan hệ" className="flex flex-wrap items-center gap-1">
      {path.map((step, i) => (
        <li key={step.id ?? i} className="flex items-center gap-1">
          {i > 0 && <span aria-hidden className="text-section-title text-info">›</span>}
          <PersonChip name={step.fullName} />
        </li>
      ))}
    </ol>
  );
}

/** Sơ đồ nhỏ: tổ tiên chung ở trên, hai nhánh đi xuống tới A và B (đường nối màu info như trên cây). */
function MiniTree({ result }: { result: Relationship }) {
  const path = result.path ?? [];
  const top = path.findIndex((step) => step.id === result.commonAncestorId);
  if (top < 0) return null;
  const toA = path.slice(0, top).reverse();
  const toB = path.slice(top + 1);
  const branches = [toA, toB].filter((branch) => branch.length > 0);
  return (
    <figure aria-label="Sơ đồ quan hệ" className="overflow-x-auto rounded-card border border-border p-4">
      <div className="mx-auto flex w-max flex-col items-center">
        <PersonChip name={path[top].fullName} />
        <div className="flex gap-8">
          {branches.map((branch, side) => (
            <div key={side} className="flex flex-col items-center">
              {branch.map((step, i) => (
                <div key={step.id ?? i} className="relative flex flex-col items-center self-stretch">
                  <span aria-hidden className="h-5 w-0.5 bg-info" />
                  {/* Hai nhánh: nối ngang từ giữa cột này sang giữa cột kia (mỗi bên vươn nửa khoảng gap-8). */}
                  {i === 0 && branches.length === 2 && (
                    <span aria-hidden className={`absolute top-0 h-0.5 bg-info ${side === 0 ? 'left-1/2 -right-4' : '-left-4 right-1/2'}`} />
                  )}
                  <PersonChip name={step.fullName} />
                </div>
              ))}
            </div>
          ))}
        </div>
      </div>
    </figure>
  );
}
