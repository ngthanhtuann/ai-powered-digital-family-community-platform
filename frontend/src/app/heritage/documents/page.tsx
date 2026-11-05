'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { HeritageHeader, HeritagePage } from '@/components/heritage/HeritageHeader';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { ApiError } from '@/lib/api';
import { heritageApi, heritageDownloadName } from '@/services/heritage';

export default function HeritageDocumentsArchivePage() {
  const access = useHeritageAccess();
  const [adminFamily, setAdminFamily] = useState('');
  const [category, setCategory] = useState('');
  const [page, setPage] = useState(0);
  const [downloadError, setDownloadError] = useState('');
  const familyId = access.isSystemAdmin ? adminFamily.trim() : undefined;
  const documents = useQuery({
    queryKey: ['heritage-documents', 'archive', category, familyId, page],
    queryFn: () =>
      heritageApi.documents({
        category: category.trim() || undefined,
        page,
        size: 20,
        ...(familyId ? { familyId } : {}),
      }),
    enabled: access.isReady && (!access.isSystemAdmin || Boolean(familyId)),
  });
  const download = useMutation({
    mutationFn: ({ contentUrl, title }: { contentUrl: string; title: string }) =>
      heritageApi.file(contentUrl).then((blob) => ({ blob, title })),
    onSuccess: ({ blob, title }) => {
      const url = URL.createObjectURL(blob);
      const link = window.document.createElement('a');
      link.href = url;
      link.download = heritageDownloadName(title || 'tai-lieu', blob);
      link.click();
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
      setDownloadError('');
    },
    onError: (reason: unknown) =>
      setDownloadError(
        reason instanceof ApiError
          ? `${reason.code}: ${reason.message}`
          : reason instanceof Error
            ? reason.message
            : 'Không thể tải tệp xuống.',
      ),
  });

  const accessError =
    (access.membership.error as Error | undefined)?.message ??
    (access.profile.error as Error | undefined)?.message;
  const dataError = (documents.error as ApiError | undefined)?.message;

  return (
    <HeritagePage>
      <HeritageHeader />
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
            onChange={(event) => {
              setAdminFamily(event.target.value);
              setPage(0);
            }}
            placeholder="UUID gia đình"
          />
        </div>
      )}
      <section className="mt-8">
        <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
          <div>
            <h2 className="text-section-title">Kho lưu trữ số</h2>
            <p className="mt-1 text-caption text-text-secondary">
              Tài liệu và tư liệu số của gia đình, được phân loại để dễ tìm.
            </p>
          </div>
          <div className="sm:w-64">
            <TextField
              label="Lọc danh mục"
              value={category}
              onChange={(event) => {
                setCategory(event.target.value);
                setPage(0);
              }}
              placeholder="Ví dụ: gia-pha"
              maxLength={50}
            />
          </div>
        </div>
        {dataError && (
          <p role="alert" className="mt-4 rounded-control bg-error-soft p-3 text-body text-error">
            {dataError}
          </p>
        )}
        {downloadError && (
          <p role="alert" className="mt-4 rounded-control bg-error-soft p-3 text-body text-error">
            {downloadError}
          </p>
        )}
        {access.isSystemAdmin && !familyId && access.isReady && (
          <p className="mt-4 text-body text-text-secondary">Nhập Family ID để xem kho lưu trữ.</p>
        )}
        {documents.isLoading && (
          <p className="mt-4 text-body text-text-secondary">Đang tải tài liệu...</p>
        )}
        {documents.data?.items.length ? (
          <ul className="mt-4 grid gap-3 sm:grid-cols-2">
            {documents.data.items.map((document) => (
              <li
                key={document.id}
                className="flex flex-col justify-between gap-4 rounded-card border border-border bg-surface p-4 shadow-card sm:flex-row sm:items-center"
              >
                <Link
                  href={`/heritage/documents/${document.id}`}
                  className="min-w-0 flex-1 hover:text-info"
                >
                  <span className="block text-section-title">{document.title}</span>
                  <span className="mt-2 block text-caption text-text-secondary">
                    {document.category || 'Chưa phân loại'} ·{' '}
                    {new Date(document.createdAt).toLocaleDateString('vi-VN')}
                  </span>
                  {document.description && (
                    <span className="mt-3 block line-clamp-2 text-body">
                      {document.description}
                    </span>
                  )}
                </Link>
                <Button
                  className="sm:w-auto"
                  disabled={download.isPending}
                  onClick={() =>
                    download.mutate({ contentUrl: document.contentUrl, title: document.title })
                  }
                >
                  Tải xuống
                </Button>
              </li>
            ))}
          </ul>
        ) : !documents.isLoading &&
          access.isReady &&
          (!access.isSystemAdmin || Boolean(familyId)) &&
          !dataError ? (
          <p className="mt-4 rounded-card border border-dashed border-border-strong p-5 text-body text-text-secondary">
            Chưa có tài liệu trong kho lưu trữ.
          </p>
        ) : null}
        {documents.data && documents.data.meta.total > 0 && (
          <div className="mt-5 flex items-center justify-between gap-3">
            <Button
              className="w-auto"
              variant="secondary"
              disabled={page === 0 || documents.isFetching}
              onClick={() => setPage((current) => current - 1)}
            >
              Trước
            </Button>
            <span className="text-caption text-text-secondary">
              Trang {page + 1} · {documents.data.meta.total} tài liệu
            </span>
            <Button
              className="w-auto"
              variant="secondary"
              disabled={
                (page + 1) * documents.data.meta.size >= documents.data.meta.total ||
                documents.isFetching
              }
              onClick={() => setPage((current) => current + 1)}
            >
              Tiếp
            </Button>
          </div>
        )}
      </section>
    </HeritagePage>
  );
}
