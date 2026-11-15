'use client';

import { useRef, useState } from 'react';
import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ProtectedImage } from '@/components/community/ProtectedPostImages';
import { Button } from '@/components/ui/Button';
import { eventsApi } from '@/services/events/events';

const MAX_IMAGE_BYTES = 5 * 1024 * 1024;

export function EventGallery({ eventId }: { eventId: string }) {
  const cache = useQueryClient();
  const [file, setFile] = useState<File | null>(null);
  const input = useRef<HTMLInputElement>(null);
  const [error, setError] = useState('');
  const query = useInfiniteQuery({
    queryKey: ['event-media', eventId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => eventsApi.media(eventId, pageParam),
    getNextPageParam: (last) =>
      (last.meta.page + 1) * last.meta.size < last.meta.total ? last.meta.page + 1 : undefined,
  });
  const upload = useMutation({
    mutationFn: () => eventsApi.uploadMedia(eventId, file!),
    onSuccess: async () => {
      setFile(null);
      if (input.current) input.current.value = '';
      await cache.invalidateQueries({ queryKey: ['event-media', eventId] });
    },
  });
  const media = query.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <section aria-label="Thư viện ảnh sự kiện" className="space-y-3 border-t border-border pt-4">
      <h4 className="font-semibold">Ảnh sự kiện</h4>
      <form
        className="flex flex-wrap items-center gap-2"
        onSubmit={(e) => {
          e.preventDefault();
          if (file) upload.mutate();
        }}
      >
        <label className="text-body" htmlFor={'event-photo-' + eventId}>
          Thêm ảnh JPG, PNG hoặc WebP (tối đa 5 MB)
        </label>
        <input
          ref={input}
          id={'event-photo-' + eventId}
          type="file"
          accept=".jpg,.jpeg,.png,.webp,image/jpeg,image/png,image/webp"
          className="max-w-full text-body"
          onChange={(e) => {
            const chosen = e.target.files?.[0] ?? null;
            setError(chosen && chosen.size > MAX_IMAGE_BYTES ? 'Ảnh vượt quá 5 MB.' : '');
            setFile(chosen && chosen.size <= MAX_IMAGE_BYTES ? chosen : null);
          }}
        />
        <Button type="submit" fullWidth={false} loading={upload.isPending} disabled={!file}>
          Tải ảnh lên
        </Button>
      </form>
      {error && (
        <p role="alert" className="text-error">
          {error}
        </p>
      )}
      {upload.isError && (
        <p role="alert" className="text-error">
          Không tải ảnh lên được.
        </p>
      )}
      {query.isPending && <p className="text-body">Đang tải thư viện ảnh...</p>}
      {query.isError && (
        <div role="alert" className="space-y-2">
          <p className="text-error">Không lấy được ảnh sự kiện.</p>
          <Button variant="secondary" fullWidth={false} onClick={() => void query.refetch()}>Thử lại</Button>
        </div>
      )}
      {media.length === 0 && !query.isPending && !query.isError && (
        <p className="text-body text-text-secondary">Chưa có ảnh sự kiện.</p>
      )}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {media.map((item) => (
          <ProtectedImage key={item.id} media={item} />
        ))}
      </div>
      {query.hasNextPage && (
        <Button
          variant="secondary"
          fullWidth={false}
          loading={query.isFetchingNextPage}
          onClick={() => query.fetchNextPage()}
        >
          Xem thêm ảnh
        </Button>
      )}
    </section>
  );
}
