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

export default function FamilyStoryDetailsPage() {
  const { id } = useParams<{ id: string }>();
  const router = useRouter();
  const queryClient = useQueryClient();
  const access = useHeritageAccess();
  const [editing, setEditing] = useState(false);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [relatedPersonId, setRelatedPersonId] = useState('');
  const [error, setError] = useState('');
  const story = useQuery({ queryKey: ['heritage-story', id], queryFn: () => heritageApi.story(id) });

  const update = useMutation({
    mutationFn: () => heritageApi.updateStory(id, {
      title: title.trim(),
      content: content.trim(),
      relatedPersonId: relatedPersonId.trim() || null,
    }),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['heritage-story', id] });
      await queryClient.invalidateQueries({ queryKey: ['heritage-stories'] });
      setEditing(false);
    },
    onError: (reason: unknown) => setError(reason instanceof ApiError
      ? `${reason.code}: ${reason.message}`
      : reason instanceof Error ? reason.message : 'Không thể cập nhật câu chuyện.'),
  });
  const remove = useMutation({
    mutationFn: () => heritageApi.deleteStory(id),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['heritage-stories'] });
      router.replace('/heritage');
    },
    onError: (reason: unknown) => setError(reason instanceof ApiError
      ? `${reason.code}: ${reason.message}`
      : reason instanceof Error ? reason.message : 'Không thể xóa câu chuyện.'),
  });

  const isLoading = story.isLoading || access.membership.isLoading || access.profile.isLoading;
  const loadError = (story.error as Error | undefined)?.message ??
    (access.membership.error as Error | undefined)?.message ??
    (access.profile.error as Error | undefined)?.message;
  const item = story.data;
  const isAuthor = item?.authorId === access.profile.data?.id;
  const canManage = access.isAdmin || isAuthor;

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
                <TextField label="Related person ID (không bắt buộc)" value={relatedPersonId} onChange={(event) => setRelatedPersonId(event.target.value)} />
                <label className="flex flex-col gap-1.5 text-label">
                  Nội dung câu chuyện
                  <textarea value={content} onChange={(event) => setContent(event.target.value)} rows={12} required className="min-h-64 rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body" />
                </label>
                <div className="flex flex-wrap gap-3">
                  <Button type="submit" className="w-auto" disabled={update.isPending}>Lưu thay đổi</Button>
                  <button type="button" className="text-label underline" onClick={() => setEditing(false)}>Hủy</button>
                </div>
              </form>
            ) : (
              <>
                <h2 className="text-page-title">{item.title}</h2>
                <p className="mt-2 text-caption text-text-secondary">
                  {item.authorName} · {new Date(item.createdAt).toLocaleDateString('vi-VN')}
                </p>
                <p className="mt-6 whitespace-pre-wrap text-body">{item.content}</p>
                {canManage && (
                  <div className="mt-6 flex flex-wrap gap-4 border-t border-border pt-4">
                    <button type="button" className="text-label text-info underline" onClick={() => {
                      setTitle(item.title);
                      setContent(item.content);
                      setRelatedPersonId(item.relatedPersonId ?? '');
                      setEditing(true);
                    }}>Sửa câu chuyện</button>
                    <button type="button" className="text-label text-error underline" onClick={() => {
                      if (window.confirm('Xóa mềm câu chuyện này?')) remove.mutate();
                    }} disabled={remove.isPending}>Xóa câu chuyện</button>
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
