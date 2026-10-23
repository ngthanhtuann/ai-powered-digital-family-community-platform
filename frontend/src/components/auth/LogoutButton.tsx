'use client';

import { useState } from 'react';
import { logout } from '@/services/auth';

// Nút Đăng xuất tạm thời; SCRUM-40 chuyển vào header/sidebar chung.
export function LogoutButton() {
  const [pending, setPending] = useState(false);
  return (
    <button
      type="button"
      disabled={pending}
      onClick={() => {
        setPending(true);
        void logout();
      }}
      className="self-start rounded-control text-label text-text-primary underline disabled:text-text-secondary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
    >
      Đăng xuất
    </button>
  );
}
