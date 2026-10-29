'use client';

import { useQuery } from '@tanstack/react-query';
import { useParams } from 'next/navigation';
import { Card } from '@/components/ui/Card';
import { api } from '@/lib/api';
import type { components } from '@/services/generated/genealogy';

type Person = components['schemas']['Person'];

export default function PersonPage() {
  const { personId } = useParams<{ personId: string }>();
  const person = useQuery({
    queryKey: ['person', personId],
    queryFn: () => api.get<Person>(`/persons/${personId}`),
    enabled: Boolean(personId),
    retry: false,
  });

  if (person.isLoading) return <p role="status">Đang tải hồ sơ…</p>;
  if (person.isError || !person.data) return <p role="alert" className="text-error">Không thể tải hồ sơ thành viên. {(person.error as Error | null)?.message}</p>;

  return (
    <Card title={person.data.fullName ?? 'Hồ sơ thành viên'} description="Thông tin được hiển thị theo quyền truy cập gia đình.">
      <dl className="grid gap-4 sm:grid-cols-2">
        {person.data.occupation && <div><dt className="text-caption text-text-secondary">Nghề nghiệp</dt><dd className="text-body">{person.data.occupation}</dd></div>}
        {person.data.currentProvince && <div><dt className="text-caption text-text-secondary">Tỉnh/thành</dt><dd className="text-body">{person.data.currentProvince}</dd></div>}
        {person.data.birthYear && <div><dt className="text-caption text-text-secondary">Năm sinh</dt><dd className="text-body">{person.data.birthYear}</dd></div>}
        <div><dt className="text-caption text-text-secondary">Giới tính</dt><dd className="text-body">{person.data.gender ?? 'Chưa cập nhật'}</dd></div>
      </dl>
    </Card>
  );
}
