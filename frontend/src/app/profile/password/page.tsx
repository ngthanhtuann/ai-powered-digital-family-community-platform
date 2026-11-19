'use client';

import { ChangePasswordForm } from '@/components/auth/AccountForms';

export default function ChangePasswordPage() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <h1 className="text-page-title lg:sr-only">Đổi mật khẩu</h1>
      <ChangePasswordForm />
    </div>
  );
}
