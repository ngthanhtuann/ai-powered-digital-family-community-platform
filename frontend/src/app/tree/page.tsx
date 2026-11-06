'use client';

import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';

/** Mục "Cây gia phả" trên menu: mở cây của gia đình mình; quản trị hệ thống chưa thuộc gia đình nào thì nhập mã. */
export default function TreeEntryPage() {
  const router = useRouter();
  const access = useHeritageAccess();
  const [familyId, setFamilyId] = useState('');

  useEffect(() => {
    if (access.familyId) router.replace(`/families/${access.familyId}/tree`);
  }, [access.familyId, router]);

  return (
    <main className="mx-auto max-w-xl space-y-4 px-4 py-8">
      <h1 className="text-page-title">Cây gia phả</h1>
      <AccessState loading={access.membership.isLoading || access.profile.isLoading}
        error={(access.membership.error as Error | null)?.message} />
      {access.isReady && !access.familyId && access.isSystemAdmin && (
        <form className="grid gap-3" onSubmit={(e) => { e.preventDefault(); router.push(`/families/${familyId.trim()}/tree`); }}>
          <TextField label="Mã gia đình" value={familyId} onChange={(e) => setFamilyId(e.target.value)} placeholder="UUID gia đình" />
          <Button type="submit" disabled={!familyId.trim()}>Xem cây</Button>
        </form>
      )}
    </main>
  );
}
