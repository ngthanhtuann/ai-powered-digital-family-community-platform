'use client';

import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { HeritageHeader, HeritagePage } from '@/components/heritage/HeritageHeader';
import { StoryForm } from '@/components/heritage/StoryForm';

export default function NewFamilyStoryPage() {
  const access = useHeritageAccess();
  const error = (access.membership.error as Error | undefined)?.message;
  return (
    <HeritagePage>
      <HeritageHeader />
      <div className="mt-6">
        <AccessState loading={access.membership.isLoading || access.profile.isLoading} error={error} />
        {access.isReady ? (
          <>
            <h2 className="mt-5 text-section-title">Viết câu chuyện gia đình</h2>
            <StoryForm />
          </>
        ) : null}
      </div>
    </HeritagePage>
  );
}
