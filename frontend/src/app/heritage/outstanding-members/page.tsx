'use client';

import Link from 'next/link';
import { useMemo, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { HeritageHeader, HeritagePage } from '@/components/heritage/HeritageHeader';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { ApiError } from '@/lib/api';
import { directoryApi } from '@/services/directory';
import { heritageApi, type OutstandingMember } from '@/services/heritage';

const PAGE_SIZE = 20;

export default function OutstandingMembersPage() {
  const access = useHeritageAccess();
  const queryClient = useQueryClient();
  const [adminFamily, setAdminFamily] = useState('');
  const [page, setPage] = useState(0);
  const [personId, setPersonId] = useState('');
  const [achievement, setAchievement] = useState('');
  const [displayOrder, setDisplayOrder] = useState('0');
  const [editing, setEditing] = useState<OutstandingMember | null>(null);
  const [editAchievement, setEditAchievement] = useState('');
  const [editOrder, setEditOrder] = useState('0');
  const familyId = access.isSystemAdmin ? adminFamily.trim() : undefined;
  const listKey = ['outstanding-members', familyId, page] as const;

  const members = useQuery({
    queryKey: listKey,
    queryFn: () =>
      heritageApi.outstandingMembers({
        ...(familyId ? { familyId } : {}),
        page,
        size: PAGE_SIZE,
        sort: 'displayOrder,asc',
      }),
    enabled: access.isReady && (!access.isSystemAdmin || Boolean(familyId)),
  });
  const people = useQuery({
    queryKey: ['outstanding-member-people', familyId],
    queryFn: async () => {
      const all = [];
      let pageNumber = 0;
      let total = Number.POSITIVE_INFINITY;
      while (all.length < total) {
        const result = await directoryApi.members({
          ...(familyId ? { familyId } : {}),
          page: pageNumber++,
          size: 100,
          sort: 'fullName,asc',
        });
        all.push(...result.items);
        total = result.meta.total;
        if (result.items.length === 0) break;
      }
      return all;
    },
    enabled: access.isReady && access.isAdmin && (!access.isSystemAdmin || Boolean(familyId)),
  });
  const invalidateList = () => queryClient.invalidateQueries({ queryKey: ['outstanding-members'] });
  const create = useMutation({
    mutationFn: () =>
      heritageApi.createOutstandingMember(
        { personId, achievement: achievement.trim(), displayOrder: Number(displayOrder) },
        familyId,
      ),
    onSuccess: () => {
      setPersonId('');
      setAchievement('');
      setDisplayOrder('0');
      void invalidateList();
    },
  });
  const update = useMutation({
    mutationFn: () =>
      heritageApi.updateOutstandingMember(editing!.id, {
        achievement: editAchievement.trim(),
        displayOrder: Number(editOrder),
      }),
    onSuccess: () => {
      setEditing(null);
      void invalidateList();
    },
  });
  const remove = useMutation({
    mutationFn: (id: string) => heritageApi.deleteOutstandingMember(id),
    onSuccess: invalidateList,
  });

  const accessError =
    (access.membership.error as Error | undefined)?.message ??
    (access.profile.error as Error | undefined)?.message;
  const dataError =
    (members.error as ApiError | undefined)?.message ??
    (people.error as ApiError | undefined)?.message ??
    (create.error as ApiError | undefined)?.message ??
    (update.error as ApiError | undefined)?.message ??
    (remove.error as ApiError | undefined)?.message;
  const existingPersonIds = useMemo(
    () => new Set((members.data?.items ?? []).map((member) => member.personId)),
    [members.data?.items],
  );
  const totalPages = Math.max(1, Math.ceil((members.data?.meta.total ?? 0) / PAGE_SIZE));
  const visiblePeople = people.data ?? [];

  function createMember(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    create.mutate();
  }

  function beginEdit(member: OutstandingMember) {
    setEditing(member);
    setEditAchievement(member.achievement);
    setEditOrder(String(member.displayOrder));
  }

  function saveEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    update.mutate();
  }

  function deleteMember(member: OutstandingMember) {
    if (window.confirm(`Xóa ${member.fullName} khỏi danh sách người tiêu biểu?`)) {
      remove.mutate(member.id);
    }
  }

  return (
    <HeritagePage>
      <HeritageHeader>
        <Link
          className="rounded-control border border-border-strong px-4 py-2 text-label hover:bg-surface-muted"
          href="/heritage"
        >
          Về kho di sản
        </Link>
      </HeritageHeader>
      <div className="mt-6">
        <AccessState loading={access.membership.isLoading || access.profile.isLoading} error={accessError} />
      </div>
      {access.isSystemAdmin && access.isReady && (
        <div className="mt-5 max-w-xl">
          <TextField
            label="Family ID (SYSTEM_ADMIN)"
            value={adminFamily}
            onChange={(event) => {
              setAdminFamily(event.target.value);
              setPage(0);
            }}
            placeholder="UUID gia đình"
          />
        </div>
      )}
      {dataError && (
        <p role="alert" className="mt-5 rounded-card border border-error bg-error-soft p-4 text-body text-error">
          {dataError}
        </p>
      )}
      {access.isReady && (!access.isSystemAdmin || Boolean(familyId)) && (
        <>
          {access.isAdmin && (
            <form onSubmit={createMember} className="mt-6 grid gap-4 rounded-card border border-border bg-surface p-5 sm:grid-cols-2">
              <label className="flex flex-col gap-2 text-label">
                Thành viên
                <select
                  className="h-[42px] rounded-control border border-border-strong bg-surface px-3 text-body text-text-primary"
                  value={personId}
                  onChange={(event) => setPersonId(event.target.value)}
                  required
                >
                  <option value="">Chọn thành viên</option>
                  {visiblePeople.map((person) => (
                    <option
                      key={person.id}
                      value={person.id}
                      disabled={existingPersonIds.has(person.id)}
                    >
                      {person.fullName}
                    </option>
                  ))}
                </select>
              </label>
              <TextField
                label="Thành tích"
                maxLength={500}
                value={achievement}
                onChange={(event) => setAchievement(event.target.value)}
                required
              />
              <TextField
                label="Thứ tự hiển thị"
                type="number"
                min={0}
                value={displayOrder}
                onChange={(event) => setDisplayOrder(event.target.value)}
                required
              />
              <div className="flex items-end">
                <Button
                  type="submit"
                  fullWidth={false}
                  loading={create.isPending}
                  disabled={!personId || !achievement.trim()}
                >
                  Thêm người tiêu biểu
                </Button>
              </div>
            </form>
          )}

          {members.isLoading ? (
            <p className="mt-6 text-body text-text-secondary">Đang tải danh sách...</p>
          ) : members.data?.items.length ? (
            <>
              <ul className="mt-6 space-y-3">
                {members.data.items.map((member) => (
                  <li key={member.id} className="rounded-card border border-border bg-surface p-5 shadow-card">
                    {editing?.id === member.id ? (
                      <form onSubmit={saveEdit} className="grid gap-4 sm:grid-cols-2">
                        <TextField
                          label="Thành tích"
                          maxLength={500}
                          value={editAchievement}
                          onChange={(event) => setEditAchievement(event.target.value)}
                          required
                        />
                        <TextField
                          label="Thứ tự hiển thị"
                          type="number"
                          min={0}
                          value={editOrder}
                          onChange={(event) => setEditOrder(event.target.value)}
                          required
                        />
                        <div className="flex gap-2">
                          <Button type="submit" fullWidth={false} loading={update.isPending}>
                            Lưu
                          </Button>
                          <Button type="button" variant="secondary" fullWidth={false} onClick={() => setEditing(null)}>
                            Hủy
                          </Button>
                        </div>
                      </form>
                    ) : (
                      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
                        <div>
                          <h2 className="text-section-title">{member.fullName}</h2>
                          <p className="mt-1 whitespace-pre-wrap text-body">{member.achievement}</p>
                          <p className="mt-1 text-caption text-text-secondary">Thứ tự {member.displayOrder}</p>
                        </div>
                        {access.isAdmin && (
                          <div className="flex gap-2">
                            <Button variant="secondary" fullWidth={false} onClick={() => beginEdit(member)}>
                              Sửa
                            </Button>
                            <Button variant="danger" fullWidth={false} loading={remove.isPending} onClick={() => deleteMember(member)}>
                              Xóa
                            </Button>
                          </div>
                        )}
                      </div>
                    )}
                  </li>
                ))}
              </ul>
              <div className="mt-6 flex items-center justify-between gap-4">
                <p className="text-caption text-text-secondary">
                  Tổng {members.data.meta.total} người · Trang {page + 1}/{totalPages}
                </p>
                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    fullWidth={false}
                    disabled={page === 0 || members.isFetching}
                    onClick={() => setPage((current) => current - 1)}
                  >
                    Trước
                  </Button>
                  <Button
                    variant="secondary"
                    fullWidth={false}
                    disabled={page + 1 >= totalPages || members.isFetching}
                    onClick={() => setPage((current) => current + 1)}
                  >
                    Sau
                  </Button>
                </div>
              </div>
            </>
          ) : (
            !members.isLoading && (
              <p className="mt-6 rounded-card border border-dashed border-border-strong p-5 text-body text-text-secondary">
                Chưa có người tiêu biểu.
              </p>
            )
          )}
        </>
      )}
    </HeritagePage>
  );
}
