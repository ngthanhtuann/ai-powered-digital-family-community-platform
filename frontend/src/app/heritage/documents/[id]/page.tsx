'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useParams, useRouter } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { HeritageHeader, HeritagePage } from '@/components/heritage/HeritageHeader';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { ApiError } from '@/lib/api';
import { heritageApi } from '@/services/heritage';

export default function HeritageDocumentDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const access = useHeritageAccess();
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [error, setError] = useState('');
  const document = useQuery({ queryKey: ['heritage-document', id], queryFn: () => heritageApi.document(id) });

  const update = useMutation({
    mutationFn: () => heritageApi.updateDocument(id, { title: title.trim(), description, category: category || null }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['heritage-document', id] });
      await queryClient.invalidateQueries({ queryKey: ['heritage-documents'] });
      setEditing(false);
    },
    onError: (reason: unknown) => setError(reason instanceof ApiError
      ? `${reason.code}: ${reason.message}`
      : reason instanceof Error ? reason.message : 'Không thể cập nhật tài liệu.'),
  });
  // Thẻ <a href> không gửi được token (lưu ở localStorage), nên tải tệp qua API rồi mở bằng blob URL.
  const openFile = useMutation({
    mutationFn: (contentUrl: string) => heritageApi.file(contentUrl),
    onSuccess: (blob) => {
      const url = URL.createObjectURL(blob);
      if (!window.open(url, '_blank')) {
        const link = window.document.createElement('a');
        link.href = url;
        link.download = document.data?.title ?? 'tai-lieu';
        link.click();
      }
      window.setTimeout(() => URL.revokeObjectURL(url), 60_000);
    },
    onError: (reason: unknown) => setError(reason instanceof ApiError
      ? `${reason.code}: ${reason.message}`
      : 'Không mở được tệp đính kèm.'),
  });
  const remove = useMutation({
    mutationFn: () => heritageApi.deleteDocument(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['heritage-documents'] });
      router.replace('/heritage');
    },
    onError: (reason: unknown) => setError(reason instanceof ApiError
      ? `${reason.code}: ${reason.message}`
      : reason instanceof Error ? reason.message : 'Không thể xóa tài liệu.'),
  });

  const isLoading = document.isLoading || access.membership.isLoading || access.profile.isLoading;
  const loadError = (document.error as Error | undefined)?.message ??
    (access.membership.error as Error | undefined)?.message ??
    (access.profile.error as Error | undefined)?.message;
  const item = document.data;

  return (
    <HeritagePage>
      <HeritageHeader />
      <div className="mt-6">
        <AccessState loading={isLoading} error={loadError} />
        {item && (
          <article className="mt-4 max-w-3xl rounded-card border border-border bg-surface p-5 shadow-card sm:p-7">
            {editing ? (
              <form
                className="flex flex-col gap-4"
                onSubmit={(event) => {
                  event.preventDefault();
                  setError('');
                  update.mutate();
                }}
              >
                <TextField label="Tiêu đề" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} required />
                <label className="flex flex-col gap-1.5 text-label">
                  Mô tả
                  <textarea value={description} onChange={(event) => setDescription(event.target.value)} rows={5} className="rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body" />
                </label>
                <TextField label="Danh mục" value={category} onChange={(event) => setCategory(event.target.value)} maxLength={50} />
                <div className="flex flex-wrap gap-3">
                  <Button type="submit" className="w-auto" disabled={update.isPending}>Lưu thay đổi</Button>
                  <button type="button" className="text-label underline" onClick={() => setEditing(false)}>Hủy</button>
                </div>
              </form>
            ) : (
              <>
                <h2 className="text-page-title">{item.title}</h2>
                <p className="mt-2 text-caption text-text-secondary">
                  {item.category || 'Chưa phân loại'} · {new Date(item.createdAt).toLocaleDateString('vi-VN')}
                </p>
                {item.description && <p className="mt-5 whitespace-pre-wrap text-body">{item.description}</p>}
                <button
                  type="button"
                  className="mt-6 inline-flex text-label text-info underline disabled:text-text-secondary"
                  disabled={openFile.isPending}
                  onClick={() => openFile.mutate(item.contentUrl)}
                >
                  {openFile.isPending ? 'Đang mở tệp...' : 'Mở tệp đính kèm'}
                </button>
                {access.isAdmin && (
                  <div className="mt-6 flex flex-wrap gap-3 border-t border-border pt-4">
                    <button type="button" className="text-label text-info underline" onClick={() => {
                      setTitle(item.title);
                      setDescription(item.description ?? '');
                      setCategory(item.category ?? '');
                      setEditing(true);
                    }}>
                      Sửa tài liệu
                    </button>
                    <button type="button" className="text-label text-error underline" onClick={() => {
                      if (window.confirm('Xóa mềm tài liệu này? Tệp đính kèm sẽ được giữ lại.')) remove.mutate();
                    }} disabled={remove.isPending}>
                      Xóa tài liệu
                    </button>
                  </div>
                )}
              </>
            )}
            {error && <p role="alert" className="mt-4 rounded-control bg-error-soft p-3 text-body text-error">{error}</p>}
          </article>
        )}
        <Link className="mt-5 inline-block text-label text-info underline" href="/heritage">Quay lại kho di sản</Link>
      </div>
    </HeritagePage>
  );
}
