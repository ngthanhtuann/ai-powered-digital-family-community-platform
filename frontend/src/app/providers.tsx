'use client';

import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { API_EVENTS } from '@/lib/api';

export function Providers({ children }: { children: React.ReactNode }) {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } },
      }),
  );
  const [forbidden, setForbidden] = useState<string | null>(null);

  // PERM_001: lib/api.ts phát sự kiện, chỗ này hiện thông báo không đủ quyền.
  useEffect(() => {
    const onForbidden = (e: Event) => {
      setForbidden(
        (e as CustomEvent<string>).detail || 'Bạn không đủ quyền thực hiện thao tác này.',
      );
    };
    window.addEventListener(API_EVENTS.forbidden, onForbidden);
    return () => window.removeEventListener(API_EVENTS.forbidden, onForbidden);
  }, []);

  return (
    <QueryClientProvider client={queryClient}>
      {forbidden && (
        <div
          role="alert"
          className="flex items-center justify-between bg-error px-4 py-2 text-sm text-white"
        >
          <span>{forbidden}</span>
          <button type="button" onClick={() => setForbidden(null)} className="underline">
            Đóng
          </button>
        </div>
      )}
      {children}
    </QueryClientProvider>
  );
}
