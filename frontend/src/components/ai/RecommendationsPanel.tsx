'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { aiApi } from '@/services/ai';

export function RecommendationsPanel({ contextType, contextId }: { contextType: string; contextId: string }) {
  const query = useQuery({ queryKey: ['ai-recommendations', contextType, contextId],
    queryFn: () => aiApi.recommendations(contextType, contextId), enabled: Boolean(contextId), retry: false });
  if (query.isLoading) return <p className="mt-4 text-sm text-text-secondary" role="status">Đang tìm nội dung liên quan…</p>;
  if (query.isError || !query.data?.recommendations.length) return null;
  return <section className="mt-5 border-t border-border pt-4" aria-label="Có thể bạn quan tâm">
    <h3 className="mb-3 text-base font-semibold">Có thể bạn quan tâm</h3>
    <ul className="grid gap-3 sm:grid-cols-2">
      {query.data.recommendations.map((item) => <li key={`${item.sourceType}-${item.sourceId}`}>
        <Link href={item.path || '/'} className="block rounded-lg border border-border p-3 hover:bg-surface-muted">
          <span className="text-xs text-text-secondary">{item.sourceType === 'PERSON' ? 'Thành viên' : 'Tài nguyên gia đình'}</span>
          <p className="font-medium">{item.title || 'Nội dung gia đình'}</p>
          {item.snippet && <p className="mt-1 line-clamp-2 text-sm text-text-secondary">{item.snippet}</p>}
        </Link>
      </li>)}
    </ul>
  </section>;
}
