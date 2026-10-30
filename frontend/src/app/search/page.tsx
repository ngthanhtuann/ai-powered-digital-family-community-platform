'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';
import { Card } from '@/components/ui/Card';
import { semanticSearch, type SemanticSearchResult } from '@/services/semantic-search';

const groups: { label: string; types: SemanticSearchResult['sourceType'][] }[] = [
  { label: 'Thành viên', types: ['PERSON'] },
  { label: 'Bài đăng', types: ['POST', 'EVENT'] },
  { label: 'Tài liệu và câu chuyện', types: ['DOCUMENT', 'STORY'] },
];

function SearchResults() {
  const params = useSearchParams();
  const query = params.get('q')?.trim() ?? '';
  const familyId = params.get('familyId')?.trim() || undefined;
  const search = useQuery({
    queryKey: ['semantic-search', query],
    queryFn: () => semanticSearch(query, 0, familyId),
    enabled: query.length > 0,
    retry: false,
  });

  return (
    <div className="mx-auto max-w-5xl space-y-5">
      <Card title="Tìm kiếm ngữ nghĩa" description={query ? `Kết quả cho “${query}”` : 'Nhập câu hỏi vào thanh tìm kiếm phía trên.'}>
        {search.isLoading && <p role="status" className="text-body text-text-secondary">Đang tìm trong dữ liệu gia đình…</p>}
        {search.isError && <p role="alert" className="text-body text-error">Không thể hoàn tất tìm kiếm. {(search.error as Error).message}</p>}
        {search.data?.results.length === 0 && <p className="text-body text-text-secondary">{search.data.message ?? 'Không tìm thấy thông tin phù hợp.'}</p>}
        {search.data && search.data.results.length > 0 && (
          <p className="text-caption text-text-secondary">Tìm thấy {search.data.total} kết quả liên quan.</p>
        )}
      </Card>

      {search.data && groups.map((group) => {
        const results = search.data.results.filter((item) => group.types.includes(item.sourceType));
        if (results.length === 0) return null;
        return (
          <section key={group.label} aria-labelledby={`search-${group.label}`} className="space-y-3">
            <h2 id={`search-${group.label}`} className="text-section-title">{group.label}</h2>
            <ul className="grid gap-3">
              {results.map((item) => (
                <li key={`${item.sourceType}-${item.sourceId}`}>
                  <Link href={item.path} className="block rounded-card border border-border bg-surface p-4 shadow-card transition-colors hover:border-secondary focus-visible:outline-2 focus-visible:outline-info">
                    <div className="flex flex-wrap items-start justify-between gap-2">
                      <h3 className="text-label text-info underline">{item.title}</h3>
                      <span className="rounded-full bg-surface-muted px-2 py-1 text-caption text-text-secondary">Độ liên quan {Math.round(item.score * 100)}%</span>
                    </div>
                    <p className="mt-2 text-body text-text-secondary">{item.snippet}</p>
                  </Link>
                </li>
              ))}
            </ul>
          </section>
        );
      })}
    </div>
  );
}

export default function SearchPage() {
  return <Suspense fallback={<p role="status">Đang tải tìm kiếm…</p>}><SearchResults /></Suspense>;
}
