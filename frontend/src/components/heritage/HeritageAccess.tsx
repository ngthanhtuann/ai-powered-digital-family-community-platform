'use client';

import { useQuery } from '@tanstack/react-query';
import { getMyMembership, getMyProfile } from '@/services/auth';
import { useAdminFamily } from '@/components/admin/AdminFamilyPicker';

export function useHeritageAccess() {
  const membership = useQuery({ queryKey: ['my-membership'], queryFn: getMyMembership });
  const profile = useQuery({ queryKey: ['my-profile'], queryFn: getMyProfile });
  const isSystemAdmin = profile.data?.roles.includes('SYSTEM_ADMIN') ?? false;
  const adminFamily = useAdminFamily();
  // Quản trị hệ thống không thuộc gia đình nào: dùng gia đình chọn trên header.
  const familyId = membership.data?.familyId ?? (isSystemAdmin ? adminFamily : null);
  const isAdmin = isSystemAdmin || membership.data?.role === 'BRANCH_ADMIN';

  return {
    membership,
    profile,
    familyId,
    isSystemAdmin,
    isAdmin,
    isReady: membership.isSuccess && profile.isSuccess,
  };
}

export function AccessState({ loading, error }: { loading: boolean; error?: string }) {
  if (loading) return <p className="text-body text-text-secondary">Đang tải thông tin thành viên...</p>;
  if (error) {
    return (
      <p role="alert" className="rounded-card border border-error bg-error-soft p-4 text-body text-error">
        {error}
      </p>
    );
  }
  return null;
}
