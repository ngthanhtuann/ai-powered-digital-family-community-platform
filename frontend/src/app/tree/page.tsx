'use client';

import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { AdminFamilyPicker } from '@/components/admin/AdminFamilyPicker';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';

/** Mục "Cây gia phả" trên menu: mở cây của gia đình mình; quản trị hệ thống chọn gia đình trong danh sách (SCRUM-118). */
export default function TreeEntryPage() {
  const router = useRouter();
  const access = useHeritageAccess();

  useEffect(() => {
    if (access.familyId) router.replace(`/families/${access.familyId}/tree`);
  }, [access.familyId, router]);

  return (
    <main className="mx-auto max-w-xl space-y-4 px-4 py-8">
      <h1 className="text-page-title">Cây gia phả</h1>
      <AccessState loading={access.membership.isLoading || access.profile.isLoading}
        error={(access.membership.error as Error | null)?.message} />
      {access.isReady && !access.familyId && access.isSystemAdmin && (
        <AdminFamilyPicker />
      )}
    </main>
  );
}
