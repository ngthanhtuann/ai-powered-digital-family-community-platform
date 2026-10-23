'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { HeritageHeader, HeritagePage } from '@/components/heritage/HeritageHeader';
import { heritageApi } from '@/services/heritage';
import { ApiError } from '@/lib/api';

export default function HeritagePageIndex() {
  const access = useHeritageAccess();
  const [category, setCategory] = useState('');
  const [adminFamily, setAdminFamily] = useState('');
  const familyId = access.isSystemAdmin ? adminFamily.trim() : undefined;
  const queryKey = useMemo(() => ['heritage-documents', category, familyId], [category, familyId]);
  const documents = useQuery({
    queryKey,
    queryFn: () =>
      heritageApi.documents({ category: category || undefined, page: 0, size: 20, ...(familyId ? { familyId } : {}) }),
    enabled: access.isReady && (!access.isSystemAdmin || Boolean(familyId)),
  });
  const stories = useQuery({
    queryKey: ['heritage-stories', familyId],
    queryFn: () => heritageApi.stories({ page: 0, size: 20, ...(familyId ? { familyId } : {}) }),
    enabled: access.isReady && (!access.isSystemAdmin || Boolean(familyId)),
  });

  const accessError =
    (access.membership.error as Error | undefined)?.message ??
    (access.profile.error as Error | undefined)?.message;
  const dataError =
    (documents.error as ApiError | undefined)?.message ?? (stories.error as ApiError | undefined)?.message;

  return (
    <HeritagePage>
      <HeritageHeader>
        {access.isAdmin && (
          <div className="flex flex-wrap gap-2">
            <Link className="rounded-control bg-secondary px-4 py-2 text-label hover:bg-gold-hover" href="/heritage/documents/new">
              Thêm tài liệu
            </Link>
            <Link className="rounded-control border border-border-strong px-4 py-2 text-label hover:bg-surface-muted" href="/heritage/stories/new">
              Viết câu chuyện
            </Link>
          </div>
        )}
      </HeritageHeader>

      <div className="mt-6">
        <AccessState loading={access.membership.isLoading || access.profile.isLoading} error={accessError} />
      </div>
      {access.isSystemAdmin && access.isReady && (
        <label className="mt-5 block max-w-xl text-label">
          Family ID (SYSTEM_ADMIN)
          <input
            value={adminFamily}
            onChange={(event) => setAdminFamily(event.target.value)}
            className="mt-1 h-11 w-full rounded-control border border-border-strong bg-surface px-3 text-body"
            placeholder="UUID gia đình"
          />
        </label>
      )}
      {dataError && (
        <p role="alert" className="mt-5 rounded-card border border-error bg-error-soft p-4 text-body text-error">
          {dataError}
        </p>
      )}

      <section className="mt-8">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="text-section-title">Tài liệu lịch sử</h2>
            <p className="mt-1 text-caption text-text-secondary">Gia phả, sắc phong, di chúc và tư liệu của gia đình.</p>
          </div>
          <label className="text-label">
            Lọc danh mục
            <input
              value={category}
              onChange={(event) => setCategory(event.target.value)}
              className="ml-2 h-10 rounded-control border border-border-strong bg-surface px-3 text-body"
              placeholder="Ví dụ: gia-pha"
              maxLength={50}
            />
          </label>
        </div>
        {documents.isLoading ? (
          <p className="mt-4 text-body text-text-secondary">Đang tải tài liệu...</p>
        ) : documents.data?.items.length ? (
          <ul className="mt-4 grid gap-3 sm:grid-cols-2">
            {documents.data.items.map((document) => (
              <li key={document.id}>
                <Link href={`/heritage/documents/${document.id}`} className="block rounded-card border border-border bg-surface p-4 shadow-card hover:border-secondary">
                  <span className="text-section-title">{document.title}</span>
                  <span className="mt-2 block text-caption text-text-secondary">
                    {document.category || 'Chưa phân loại'} · {new Date(document.createdAt).toLocaleDateString('vi-VN')}
                  </span>
                  {document.description && <span className="mt-3 block line-clamp-2 text-body">{document.description}</span>}
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-4 rounded-card border border-dashed border-border-strong p-5 text-body text-text-secondary">
            Chưa có tài liệu trong danh mục này.
          </p>
        )}
      </section>

      <section className="mt-10">
        <div className="flex items-end justify-between gap-3">
          <div>
            <h2 className="text-section-title">Câu chuyện gia đình</h2>
            <p className="mt-1 text-caption text-text-secondary">Ký ức và câu chuyện được các thành viên chia sẻ.</p>
          </div>
          {!access.isAdmin && access.isReady && (
            <Link className="text-label text-info underline" href="/heritage/stories/new">Đăng câu chuyện</Link>
          )}
        </div>
        {stories.isLoading ? (
          <p className="mt-4 text-body text-text-secondary">Đang tải câu chuyện...</p>
        ) : stories.data?.items.length ? (
          <ul className="mt-4 grid gap-3 sm:grid-cols-2">
            {stories.data.items.map((story) => (
              <li key={story.id}>
                <Link href={`/heritage/stories/${story.id}`} className="block rounded-card border border-border bg-surface p-4 shadow-card hover:border-secondary">
                  <span className="text-section-title">{story.title}</span>
                  <span className="mt-2 block text-caption text-text-secondary">
                    {story.authorName} · {new Date(story.createdAt).toLocaleDateString('vi-VN')}
                  </span>
                  <span className="mt-3 block line-clamp-3 whitespace-pre-wrap text-body">{story.content}</span>
                </Link>
              </li>
            ))}
          </ul>
        ) : (
          <p className="mt-4 rounded-card border border-dashed border-border-strong p-5 text-body text-text-secondary">
            Chưa có câu chuyện nào.
          </p>
        )}
      </section>
    </HeritagePage>
  );
}
