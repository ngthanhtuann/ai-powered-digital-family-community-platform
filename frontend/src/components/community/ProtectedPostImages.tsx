'use client';

import { useEffect, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { communityApi, type Media } from '@/services/community/posts';

export function ProtectedImage({ media }: { media: Media }) {
  const [src, setSrc] = useState<string>();
  const [failed, setFailed] = useState(false);
  const [retryAttempt, setRetryAttempt] = useState(0);

  useEffect(() => {
    let active = true;
    let currentUrl: string | undefined;
    setSrc(undefined);
    setFailed(false);
    const path = media.contentUrl.startsWith('/api/v1/')
      ? media.contentUrl.substring('/api/v1'.length) : media.contentUrl;
    api.download(path).then((blob) => {
      if (!active) return;
      currentUrl = URL.createObjectURL(blob);
      setSrc(currentUrl);
    }).catch(() => { if (active) setFailed(true); });
    return () => { active = false; if (currentUrl) URL.revokeObjectURL(currentUrl); };
  }, [media.contentUrl, retryAttempt]);

  if (failed) return (
    <div role="alert" className="space-y-1 text-sm text-error">
      <p>Không tải được ảnh.</p>
      <button type="button" className="min-h-11 underline" onClick={() => setRetryAttempt((attempt) => attempt + 1)}>Thử lại</button>
    </div>
  );
  if (!src) return <p className="text-sm text-text-secondary">Đang tải ảnh...</p>;
  // Blob được tải qua API có JWT; URL này chỉ tồn tại trong trình duyệt hiện tại.
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt={media.caption || media.fileName} className="w-full rounded-lg object-cover" />;
}

/** Ảnh xem trước đi kèm bài trong danh sách; chỉ gọi /posts/{id}/media khi người dùng muốn xem thêm. */
export function ProtectedPostImages({ postId, preview, total }: { postId: string; preview: Media[]; total: number }) {
  const [expanded, setExpanded] = useState(false);
  const query = useInfiniteQuery({
    queryKey: ['post-media', postId],
    initialPageParam: 0,
    enabled: expanded,
    queryFn: ({ pageParam }) => communityApi.postMedia(postId, pageParam),
    getNextPageParam: (last) =>
      (last.meta.page + 1) * last.meta.size < last.meta.total ? last.meta.page + 1 : undefined,
  });
  const images = expanded && query.data ? query.data.pages.flatMap((page) => page.items) : preview;
  const hasMore = expanded ? Boolean(query.hasNextPage) : total > preview.length;
  if (images.length === 0 && !hasMore && (!expanded || (!query.isPending && !query.isError))) return null;
  return (
    <section className="mt-3 space-y-2" aria-label="Ảnh của bài đăng">
      {expanded && query.isPending && <p className="text-sm text-text-secondary">Đang tải danh sách ảnh...</p>}
      {query.isError && (
        <div role="alert" className="space-y-2">
          <p className="text-sm text-error">Không lấy được danh sách ảnh.</p>
          <button type="button" className="min-h-11 text-sm underline" onClick={() => void query.refetch()}>
            Thử lại
          </button>
        </div>
      )}
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {images.map((media) => <ProtectedImage key={media.id} media={media} />)}
      </div>
      {hasMore && <button type="button" className="min-h-11 text-sm underline"
        disabled={query.isFetching} onClick={() => expanded ? query.fetchNextPage() : setExpanded(true)}>
        {query.isFetching ? 'Đang tải...' : `Xem thêm ảnh (${total} ảnh)`}
      </button>}
    </section>
  );
}
