'use client';

import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { HeritageHeader, HeritagePage } from '@/components/heritage/HeritageHeader';
import { DocumentForm } from '@/components/heritage/DocumentForm';

export default function NewHeritageDocumentPage() {
  const access = useHeritageAccess();
  const error = (access.membership.error as Error | undefined)?.message;
  return (
    <HeritagePage>
      <HeritageHeader />
      <div className="mt-6">
        <AccessState loading={access.membership.isLoading || access.profile.isLoading} error={error} />
        {access.isReady && access.isAdmin ? (
          <>
            <h2 className="mt-5 text-section-title">Thêm tài liệu lịch sử</h2>
            <DocumentForm />
          </>
        ) : access.isReady ? (
          <p role="alert" className="mt-5 text-body text-error">Chỉ quản trị viên được thêm tài liệu.</p>
        ) : null}
      </div>
    </HeritagePage>
  );
}
