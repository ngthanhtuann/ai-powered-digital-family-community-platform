'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '@/lib/api';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { heritageApi } from '@/services/heritage';
import { useHeritageAccess } from './HeritageAccess';

export function DocumentForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const access = useHeritageAccess();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [category, setCategory] = useState('');
  const [file, setFile] = useState<File | null>(null);
  const [familyId, setFamilyId] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);

  const create = useMutation({
    mutationFn: async () => {
      const targetFamilyId = access.isSystemAdmin ? familyId.trim() : access.familyId;
      if (!targetFamilyId) throw new Error('Cần có family ID trước khi thêm tài liệu.');
      if (!file) throw new Error('Chọn tệp đính kèm trước khi lưu.');
      setBusy(true);
      const media = await heritageApi.upload(file, targetFamilyId);
      return heritageApi.createDocument(
        {
          title: title.trim(),
          description: description.trim() || null,
          category: category.trim() || null,
          mediaId: media.id,
        },
        access.isSystemAdmin ? targetFamilyId : undefined,
      );
    },
    onSuccess: async (document) => {
      await queryClient.invalidateQueries({ queryKey: ['heritage-documents'] });
      router.push(`/heritage/documents/${document.id}`);
    },
    onError: (reason: unknown) => {
      setError(reason instanceof ApiError
        ? `${reason.code}: ${reason.message}`
        : reason instanceof Error ? reason.message : 'Không thể thêm tài liệu.');
    },
    onSettled: () => setBusy(false),
  });

  return (
    <form
      className="mt-6 flex max-w-2xl flex-col gap-4 rounded-card border border-border bg-surface p-5 shadow-card"
      onSubmit={(event) => {
        event.preventDefault();
        setError('');
        create.mutate();
      }}
    >
      {access.isSystemAdmin && (
        <TextField label="Family ID" value={familyId} onChange={(event) => setFamilyId(event.target.value)} required />
      )}
      <TextField label="Tiêu đề" value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} required />
      <label className="flex flex-col gap-1.5 text-label">
        Mô tả
        <textarea
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          rows={4}
          className="rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body outline-none focus-visible:outline-2 focus-visible:outline-info"
        />
      </label>
      <TextField label="Danh mục" value={category} onChange={(event) => setCategory(event.target.value)} maxLength={50} placeholder="gia-pha, sac-phong..." />
      <label className="flex flex-col gap-1.5 text-label">
        Tệp đính kèm (JPG, PNG, WEBP tối đa 5 MB; PDF tối đa 10 MB)
        <input
          type="file"
          accept=".jpg,.jpeg,.png,.webp,.pdf,image/jpeg,image/png,image/webp,application/pdf"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
          required
          className="rounded-control border border-border bg-surface p-2 text-body"
        />
      </label>
      {error && <p role="alert" className="rounded-control bg-error-soft p-3 text-body text-error">{error}</p>}
      <Button type="submit" disabled={busy || !access.isReady}>
        {busy ? 'Đang tải và lưu...' : 'Thêm tài liệu'}
      </Button>
    </form>
  );
}
