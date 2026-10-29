'use client';

import Link from 'next/link';
import { useMemo, useState, type FormEvent } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { TextField } from '@/components/ui/TextField';
import { Button } from '@/components/ui/Button';
import { ApiError } from '@/lib/api';
import { directoryApi } from '@/services/directory';

const PAGE_SIZE = 20;

export default function DirectoryPage() {
  const access = useHeritageAccess();
  const [adminFamily, setAdminFamily] = useState('');
  const [draft, setDraft] = useState({ job: '', location: '', generation: '' });
  const [filters, setFilters] = useState({ job: '', location: '', generation: '' });
  const [page, setPage] = useState(0);
  const familyId = access.isSystemAdmin ? adminFamily.trim() : undefined;

  const members = useQuery({
    queryKey: ['directory-members', familyId, filters, page],
    queryFn: () =>
      directoryApi.members({
        ...(familyId ? { familyId } : {}),
        ...(filters.job ? { job: filters.job } : {}),
        ...(filters.location ? { location: filters.location } : {}),
        ...(filters.generation ? { generation: Number(filters.generation) } : {}),
        page,
        size: PAGE_SIZE,
        sort: 'fullName,asc',
      }),
    enabled: access.isReady && (!access.isSystemAdmin || Boolean(familyId)),
  });

  const accessError =
    (access.membership.error as Error | undefined)?.message ??
    (access.profile.error as Error | undefined)?.message;
  const dataError = (members.error as ApiError | undefined)?.message;
  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((members.data?.meta.total ?? 0) / PAGE_SIZE)),
    [members.data?.meta.total],
  );

  function applyFilters(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(0);
    setFilters({ ...draft });
  }

  return (
    <main className="mx-auto min-h-screen max-w-5xl px-4 py-8 sm:px-6">
      <header className="border-b border-border pb-5">
        <p className="text-caption text-text-secondary">Cộng đồng gia đình</p>
        <h1 className="mt-1 text-page-title">Danh bạ thành viên</h1>
        <p className="mt-2 text-body text-text-secondary">
          Tra cứu hồ sơ thành viên trong gia đình.
        </p>
      </header>

      <div className="mt-6">
        <AccessState
          loading={access.membership.isLoading || access.profile.isLoading}
          error={accessError}
        />
      </div>
      {access.isSystemAdmin && access.isReady && (
        <div className="mt-5 max-w-xl">
          <TextField
            label="Family ID (SYSTEM_ADMIN)"
            value={adminFamily}
            onChange={(event) => setAdminFamily(event.target.value)}
            placeholder="UUID gia đình"
          />
        </div>
      )}

      {access.isReady && (!access.isSystemAdmin || familyId) && (
        <>
          <form
            onSubmit={applyFilters}
            className="mt-6 grid gap-4 rounded-card border border-border bg-surface p-5 sm:grid-cols-2 lg:grid-cols-4"
          >
            <TextField
              label="Nghề nghiệp"
              maxLength={100}
              value={draft.job}
              onChange={(event) => setDraft({ ...draft, job: event.target.value })}
              placeholder="Ví dụ: giáo viên"
            />
            <TextField
              label="Tỉnh/thành đang ở"
              maxLength={100}
              value={draft.location}
              onChange={(event) => setDraft({ ...draft, location: event.target.value })}
              placeholder="Ví dụ: Đà Nẵng"
            />
            <TextField
              label="Thế hệ"
              type="number"
              min={1}
              value={draft.generation}
              onChange={(event) => setDraft({ ...draft, generation: event.target.value })}
              placeholder="Không lọc"
            />
            <div className="flex items-end">
              <Button type="submit" fullWidth={false}>
                Tìm thành viên
              </Button>
            </div>
          </form>

          {dataError && (
            <p
              role="alert"
              className="mt-5 rounded-card border border-error bg-error-soft p-4 text-body text-error"
            >
              {dataError}
            </p>
          )}
          {members.isLoading ? (
            <p className="mt-6 text-body text-text-secondary">Đang tải danh bạ...</p>
          ) : members.data?.items.length ? (
            <>
              <ul className="mt-6 grid gap-3 sm:grid-cols-2">
                {members.data.items.map((member) => (
                  <li key={member.id}>
                    <Link
                      href={`/directory/members/${member.id}`}
                      className="block rounded-card border border-border bg-surface p-5 shadow-card transition-colors hover:border-secondary"
                    >
                      <span className="text-section-title">{member.fullName}</span>
                      <span className="mt-2 block text-caption text-text-secondary">
                        {member.relationship || 'Chưa có thông tin quan hệ'}
                        {member.birthYear ? ` · Sinh năm ${member.birthYear}` : ''}
                      </span>
                      <span className="mt-3 block text-body">
                        {member.jobTitle || 'Chưa có thông tin nghề nghiệp'}
                        {member.location ? ` · ${member.location}` : ''}
                      </span>
                      {member.generation != null && (
                        <span className="mt-2 block text-caption text-text-secondary">
                          Thế hệ {member.generation}
                        </span>
                      )}
                    </Link>
                  </li>
                ))}
              </ul>
              <div className="mt-6 flex items-center justify-between gap-4">
                <p className="text-caption text-text-secondary">
                  Tổng {members.data.meta.total} thành viên · Trang {page + 1}/{totalPages}
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
                Không tìm thấy thành viên phù hợp.
              </p>
            )
          )}
        </>
      )}
    </main>
  );
}
