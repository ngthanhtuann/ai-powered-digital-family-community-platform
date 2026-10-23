'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { LogoutButton } from '@/components/auth/LogoutButton';
import { checkHealth } from '@/lib/api';

export default function HomePage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['health'],
    queryFn: checkHealth,
  });

  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-3 px-6">
      <h1 className="text-page-title">Hello FamilyConnect</h1>
      <p className="text-body text-text-secondary">
        Backend: {isLoading && 'đang kiểm tra...'}
        {isError && (
          <span className="text-error">không kết nối được ({(error as Error).message})</span>
        )}
        {data && <span className="text-success">{data.status}</span>}
      </p>
      <Link href="/heritage" className="inline-flex w-fit rounded-control border border-secondary bg-secondary px-4 py-2 text-label hover:bg-gold-hover">
        Mở kho di sản
      </Link>
      <LogoutButton />
    </main>
  );
}
