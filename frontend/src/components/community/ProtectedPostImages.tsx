'use client';

import { useEffect, useState } from 'react';
import { useInfiniteQuery } from '@tanstack/react-query';
import { api } from '@/lib/api';
import { communityApi, type Media } from '@/services/community/posts';

function ProtectedImage({ media }: { media: Media }) {
  const [src, setSrc] = useState<string>();
  const [failed, setFailed] = useState(false);

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
  }, [media.contentUrl]);

  if (failed) return <p role="alert" className="text-sm text-error">Không tải được ảnh.</p>;
  if (!src) return <p className="text-sm text-text-secondary">Đang tải ảnh...</p>;
  // Blob được tải qua API có JWT; URL này chỉ tồn tại trong trình duyệt hiện tại.
  // eslint-disable-next-line @next/next/no-img-element
  return <img src={src} alt={media.caption || media.fileName} className="w-full rounded-lg object-cover" />;
}

export function ProtectedPostImages({ postId }: { postId: string }) {
  const query = useInfiniteQuery({
    queryKey: ['post-media', postId],
    initialPageParam: 0,
    queryFn: ({ pageParam }) => communityApi.postMedia(postId, pageParam),
    getNextPageParam: (last) =>
      (last.meta.page + 1) * last.meta.size < last.meta.total ? last.meta.page + 1 : undefined,
  });
  const images = query.data?.pages.flatMap((page) => page.items) ?? [];
  return (
    <section className="mt-3 space-y-2" aria-label="Ảnh của bài đăng">
      {query.isError && <p role="alert" className="text-sm text-error">Không lấy được danh sách ảnh.</p>}
      {images.length > 0 && <div className="grid grid-cols-2 gap-2 sm:grid-cols-3">
        {images.map((media) => <ProtectedImage key={media.id} media={media} />)}
      </div>}
      {query.hasNextPage && <button type="button" className="text-sm underline"
        disabled={query.isFetchingNextPage} onClick={() => query.fetchNextPage()}>
        {query.isFetchingNextPage ? 'Đang tải...' : 'Xem thêm ảnh'}
      </button>}
    </section>
  );
}
