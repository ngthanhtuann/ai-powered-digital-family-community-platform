'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '@/lib/api';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { heritageApi } from '@/services/heritage';
import { useHeritageAccess } from './HeritageAccess';

export function StoryForm() {
  const router = useRouter();
  const queryClient = useQueryClient();
  const access = useHeritageAccess();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [relatedPersonId, setRelatedPersonId] = useState('');
  const [familyId, setFamilyId] = useState('');
  const [error, setError] = useState('');

  const create = useMutation({
    mutationFn: () => {
      const targetFamilyId = access.isSystemAdmin ? familyId.trim() : access.familyId;
      if (!targetFamilyId) throw new Error('Cần có family ID trước khi đăng câu chuyện.');
      return heritageApi.createStory(
        { title: title.trim(), content: content.trim(), relatedPersonId: relatedPersonId.trim() || null },
        targetFamilyId,
      );
    },
    onSuccess: async (story) => {
      await queryClient.invalidateQueries({ queryKey: ['heritage-stories'] });
      router.push(`/heritage/stories/${story.id}`);
    },
    onError: (reason: unknown) => {
      setError(reason instanceof ApiError ? `${reason.code}: ${reason.message}` : (reason as Error).message);
    },
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
      <TextField
        label="Related person ID (không bắt buộc)"
        value={relatedPersonId}
        onChange={(event) => setRelatedPersonId(event.target.value)}
        placeholder="UUID"
      />
      <label className="flex flex-col gap-1.5 text-label">
        Nội dung câu chuyện
        <textarea
          value={content}
          onChange={(event) => setContent(event.target.value)}
          rows={12}
          required
          className="min-h-64 rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body outline-none focus-visible:outline-2 focus-visible:outline-info"
          placeholder="Ghi lại câu chuyện, ký ức hoặc dấu mốc của gia đình..."
        />
        <span className="text-caption text-text-secondary">Trình soạn thảo văn bản đơn giản · {content.length} ký tự</span>
      </label>
      {error && <p role="alert" className="rounded-control bg-error-soft p-3 text-body text-error">{error}</p>}
      <Button type="submit" disabled={create.isPending || !access.isReady}>
        {create.isPending ? 'Đang đăng...' : 'Đăng câu chuyện'}
      </Button>
    </form>
  );
}
