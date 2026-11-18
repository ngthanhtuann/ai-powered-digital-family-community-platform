'use client';

import { useQuery } from '@tanstack/react-query';
import { useSyncExternalStore } from 'react';
import { dashboardApi } from '@/services/dashboard';

// Gia đình Quản trị hệ thống đang xem (SCRUM-117): chọn một lần trên header, mọi trang dùng chung qua useHeritageAccess.
const KEY = 'fc-admin-family';
const listeners = new Set<() => void>();

function read(): string | null {
  try {
    return window.localStorage.getItem(KEY);
  } catch {
    return null; // trình duyệt chặn lưu trữ: coi như chưa chọn
  }
}

export function setAdminFamily(id: string) {
  try {
    if (id) window.localStorage.setItem(KEY, id);
    else window.localStorage.removeItem(KEY);
  } catch {
    // vẫn báo thay đổi để giao diện cập nhật trong phiên này
  }
  listeners.forEach((notify) => notify());
}

function subscribe(notify: () => void) {
  listeners.add(notify);
  return () => listeners.delete(notify);
}

export function useAdminFamily(): string | null {
  return useSyncExternalStore(subscribe, read, () => null);
}

export function AdminFamilyPicker() {
  const selected = useAdminFamily();
  const families = useQuery({ queryKey: ['dashboard-families'], queryFn: dashboardApi.families });
  return (
    <label className="flex min-w-0 items-center gap-2 text-caption text-text-secondary">
      <span className="hidden md:inline">Đang xem</span>
      <select className="h-11 min-w-0 max-w-[14rem] rounded-control border border-border bg-surface px-2 text-body text-text-primary"
        aria-label="Gia đình đang xem" value={selected ?? ''} onChange={(e) => setAdminFamily(e.target.value)}
        disabled={!families.data}>
        <option value="">{families.isError ? 'Lỗi tải gia đình' : 'Chọn gia đình'}</option>
        {families.data?.map((f) => <option key={f.id} value={f.id}>{f.name}</option>)}
      </select>
    </label>
  );
}
