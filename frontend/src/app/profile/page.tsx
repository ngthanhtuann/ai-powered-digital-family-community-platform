'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { ProfileForm } from '@/components/auth/AccountForms';
import { genealogyApi } from '@/services/genealogy';

export default function ProfilePage() {
  const me = useQuery({ queryKey: ['person', 'me'], queryFn: genealogyApi.myPerson, retry: false });
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <h1 className="text-page-title lg:sr-only">Hồ sơ cá nhân</h1>
      {me.data && (
        <Link href={`/persons/${me.data.id}`}
          className="flex min-h-11 items-center justify-between rounded-card border border-border bg-surface p-4 text-label hover:border-secondary">
          Xem trang cá nhân của tôi (ảnh, gia phả, bài đăng) <span aria-hidden="true">→</span>
        </Link>
      )}
      {me.isError && (
        <p className="rounded-card bg-surface-muted p-4 text-body text-text-secondary">
          Tài khoản chưa gắn với hồ sơ trong gia phả nên chưa có trang cá nhân. Nhờ Trưởng chi gắn hồ sơ cho bạn.
        </p>
      )}
      <ProfileForm />
    </div>
  );
}
