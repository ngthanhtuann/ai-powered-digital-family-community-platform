'use client';

import Link from 'next/link';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { aiApi, type AiRecommendationContext } from '@/services/ai';

/**
 * Khối "Có thể bạn quan tâm" (UC-AI-05). deferred: chỉ tải khi người dùng bấm, dùng trong feed
 * để không gọi API cho mọi bài đang hiện.
 */
export function RecommendationsPanel({
  contextType,
  contextId,
  deferred = false,
}: {
  contextType: AiRecommendationContext;
  contextId: string;
  deferred?: boolean;
}) {
  const [open, setOpen] = useState(!deferred);
  const query = useQuery({
    queryKey: ['ai-recommendations', contextType, contextId],
    queryFn: () => aiApi.recommendations(contextType, contextId),
    enabled: open && Boolean(contextId),
    retry: false,
  });

  if (!open) {
    return (
      <div className="mt-4">
        <Button type="button" variant="secondary" fullWidth={false} onClick={() => setOpen(true)}>
          Xem nội dung liên quan
        </Button>
      </div>
    );
  }
  if (query.isLoading) {
    return (
      <p className="mt-4 text-body text-text-secondary" role="status">
        Đang tìm nội dung liên quan…
      </p>
    );
  }
  if (query.isError) {
    // Tự tải (trang hồ sơ): ẩn khối khi lỗi để không chặn nội dung chính; người dùng bấm xem thì báo lỗi.
    return deferred ? (
      <p role="alert" className="mt-4 text-body text-error">
        {query.error instanceof Error ? query.error.message : 'Không tải được nội dung liên quan.'}
      </p>
    ) : null;
  }
  const items = query.data?.recommendations ?? [];
  if (!items.length) {
    return deferred ? <p className="mt-4 text-body text-text-secondary">Chưa có nội dung liên quan.</p> : null;
  }
  return (
    <section className="mt-5 border-t border-border pt-4" aria-label="Có thể bạn quan tâm">
      <h3 className="mb-3 text-section-title">Có thể bạn quan tâm</h3>
      <ul className="grid gap-3 md:grid-cols-2">
        {items.map((item) => (
          <li key={`${item.sourceType}-${item.sourceId}`}>
            <Link
              href={item.path || '/'}
              className="block rounded-card border border-border p-3 hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
            >
              <span className="text-caption text-text-secondary">
                {item.sourceType === 'PERSON' ? 'Thành viên' : 'Tài nguyên gia đình'}
              </span>
              <p className="text-label">{item.title || 'Nội dung gia đình'}</p>
              {item.reason && <p className="mt-1 text-caption text-text-secondary">{item.reason}</p>}
              {item.snippet && (
                <p className="mt-1 line-clamp-2 text-body text-text-secondary">{item.snippet}</p>
              )}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}
