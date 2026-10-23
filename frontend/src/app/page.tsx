'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { Card } from '@/components/ui/Card';
import { checkHealth } from '@/lib/api';

export default function HomePage() {
  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['health'],
    queryFn: checkHealth,
  });

  return (
    <div className="grid gap-4 lg:grid-cols-2">
      <Card title="Chào mừng đến FamilyConnect" description="Kết nối các thế hệ trong gia đình.">
        <p className="text-body text-text-secondary">
          Trạng thái máy chủ: {isLoading && <span>đang kiểm tra...</span>}
          {isError && (
            <span className="text-error">không kết nối được ({(error as Error).message})</span>
          )}
          {data && <span className="font-semibold text-success">{data.status}</span>}
        </p>
      </Card>
      <Card
        title="Thư viện thành phần"
        description="Xem hướng dẫn và ví dụ các component dùng chung."
      >
        <Link href="/ui-kit" className="text-label text-info underline">
          Mở trang hướng dẫn UI kit
        </Link>
      </Card>
    </div>
  );
}
