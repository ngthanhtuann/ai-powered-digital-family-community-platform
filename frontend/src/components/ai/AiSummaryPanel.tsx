'use client';

import { useMutation } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { aiApi, type SummarySourceType } from '@/services/ai';

export function AiSummaryPanel({ sourceType, sourceId }: { sourceType: SummarySourceType; sourceId: string }) {
  const summary = useMutation({ mutationFn: () => aiApi.summarize(sourceType, sourceId) });
  return <section className="mt-5 border-t border-border pt-4">
    <Button type="button" fullWidth={false} variant="secondary" loading={summary.isPending}
      onClick={() => summary.mutate()}>{summary.isPending ? 'Đang tóm tắt...' : 'Tóm tắt bằng AI'}</Button>
    {summary.isError && <p role="alert" className="mt-3 text-sm text-error">
      {summary.error instanceof Error ? summary.error.message : 'AI tạm thời không khả dụng. Vui lòng thử lại.'}
    </p>}
    {summary.data && <div className="mt-4 rounded-lg bg-surface-muted p-4">
      <p className="mb-2 text-xs font-semibold text-text-secondary">Do AI tạo</p>
      <p className="whitespace-pre-wrap text-body">{summary.data.summary}</p>
    </div>}
  </section>;
}
