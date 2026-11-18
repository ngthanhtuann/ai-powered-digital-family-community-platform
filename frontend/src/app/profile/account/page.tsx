'use client';

import { ProfileForm } from '@/components/auth/AccountForms';

/** Thông tin tài khoản đăng nhập (UC-AUTH-05), mở từ nút "Tài khoản đăng nhập" trên trang cá nhân (SCRUM-119). */
export default function AccountPage() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <h1 className="text-page-title lg:sr-only">Tài khoản đăng nhập</h1>
      <ProfileForm />
    </div>
  );
}
