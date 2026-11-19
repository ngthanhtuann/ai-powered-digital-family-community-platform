'use client';

import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { ProfileForm } from '@/components/auth/AccountForms';
import { genealogyApi } from '@/services/genealogy';

/**
 * "Hồ sơ cá nhân" mở thẳng trang cá nhân của mình (SCRUM-119); sửa hồ sơ bằng nút trên trang đó.
 * Tài khoản chưa gắn hồ sơ gia phả thì chỉ có thông tin tài khoản.
 */
export default function ProfilePage() {
  const router = useRouter();
  const me = useQuery({ queryKey: ['person', 'me'], queryFn: genealogyApi.myPerson, retry: false });
  const personId = me.data?.id;
  useEffect(() => {
    if (personId) router.replace(`/persons/${personId}`);
  }, [personId, router]);

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <h1 className="text-page-title lg:sr-only">Hồ sơ cá nhân</h1>
      {(me.isPending || personId) && <p role="status" className="text-body text-text-secondary">Đang mở trang cá nhân...</p>}
      {me.isError && (
        <>
          <p className="rounded-card bg-surface-muted p-4 text-body text-text-secondary">
            Tài khoản chưa gắn với hồ sơ trong gia phả nên chưa có trang cá nhân. Nhờ Trưởng chi gắn hồ sơ cho bạn.
          </p>
          <ProfileForm />
        </>
      )}
    </div>
  );
}
