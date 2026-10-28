'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState, type FormEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError } from '@/lib/api';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { directoryApi, type EducationProfile, type ProfessionProfile } from '@/services/directory';

type ProfileTab = 'profession' | 'education';

function isMissingProfile(error: unknown) {
  return error instanceof ApiError && error.status === 404;
}

export default function DirectoryMemberPage() {
  const params = useParams<{ personId: string }>();
  const personId = params.personId;
  const access = useHeritageAccess();
  const [tab, setTab] = useState<ProfileTab>('profession');
  const profession = useQuery({
    queryKey: ['directory-profession-profile', personId],
    queryFn: async () => {
      try {
        return await directoryApi.professionProfile(personId);
      } catch (error) {
        if (isMissingProfile(error)) return null;
        throw error;
      }
    },
    enabled: access.isReady && Boolean(personId),
  });
  const education = useQuery({
    queryKey: ['directory-education-profile', personId],
    queryFn: async () => {
      try {
        return await directoryApi.educationProfile(personId);
      } catch (error) {
        if (isMissingProfile(error)) return null;
        throw error;
      }
    },
    enabled: access.isReady && Boolean(personId),
  });
  const error =
    (access.membership.error as Error | undefined)?.message ??
    (access.profile.error as Error | undefined)?.message ??
    (profession.error as ApiError | undefined)?.message ??
    (education.error as ApiError | undefined)?.message;

  return (
    <main className="mx-auto min-h-screen max-w-3xl px-4 py-8 sm:px-6">
      <Link href="/directory" className="text-caption text-info underline">
        Quay lại danh bạ
      </Link>
      <header className="mt-4 border-b border-border pb-5">
        <p className="text-caption text-text-secondary">Hồ sơ thành viên</p>
        <h1 className="mt-1 text-page-title">Nghề nghiệp và học vấn</h1>
        <p className="mt-2 text-caption text-text-secondary">Mã thành viên: {personId}</p>
      </header>
      <div className="mt-6">
        <AccessState
          loading={access.membership.isLoading || access.profile.isLoading}
          error={error}
        />
      </div>
      {error && (
        <p role="alert" className="mt-4 text-body text-error">
          {error}
        </p>
      )}

      {access.isReady && (
        <>
          <div
            className="mt-6 flex gap-2 border-b border-border"
            role="tablist"
            aria-label="Thông tin hồ sơ"
          >
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'profession'}
              aria-controls="profession-panel"
              className={`px-4 py-3 text-label ${tab === 'profession' ? 'border-b-2 border-secondary' : 'text-text-secondary'}`}
              onClick={() => setTab('profession')}
            >
              Nghề nghiệp
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={tab === 'education'}
              aria-controls="education-panel"
              className={`px-4 py-3 text-label ${tab === 'education' ? 'border-b-2 border-secondary' : 'text-text-secondary'}`}
              onClick={() => setTab('education')}
            >
              Học vấn
            </button>
          </div>
          <div id="profession-panel" role="tabpanel" hidden={tab !== 'profession'}>
            <ProfessionPanel
              personId={personId}
              profile={profession.data ?? null}
              loading={profession.isLoading}
            />
          </div>
          <div id="education-panel" role="tabpanel" hidden={tab !== 'education'}>
            <EducationPanel
              personId={personId}
              profile={education.data ?? null}
              loading={education.isLoading}
            />
          </div>
        </>
      )}
    </main>
  );
}

function ProfessionPanel({
  personId,
  profile,
  loading,
}: {
  personId: string;
  profile: ProfessionProfile | null;
  loading: boolean;
}) {
  const queryClient = useQueryClient();
  const [jobTitle, setJobTitle] = useState(profile?.jobTitle ?? '');
  const [company, setCompany] = useState(profile?.company ?? '');
  const [workLocation, setWorkLocation] = useState(profile?.workLocation ?? '');
  useEffect(() => {
    setJobTitle(profile?.jobTitle ?? '');
    setCompany(profile?.company ?? '');
    setWorkLocation(profile?.workLocation ?? '');
  }, [profile]);
  const mutation = useMutation({
    mutationFn: () =>
      directoryApi.saveProfessionProfile(personId, {
        jobTitle: jobTitle.trim(),
        company: company.trim() || null,
        workLocation: workLocation.trim() || null,
      }),
    onSuccess: async (saved) => {
      queryClient.setQueryData(['directory-profession-profile', personId], saved);
      await queryClient.invalidateQueries({ queryKey: ['directory-members'] });
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate();
  }

  if (loading) return <p className="mt-6 text-body text-text-secondary">Đang tải hồ sơ...</p>;
  return (
    <form
      onSubmit={submit}
      className="mt-6 grid gap-4 rounded-card border border-border bg-surface p-5"
    >
      <p className="text-body text-text-secondary">
        {profile ? 'Cập nhật hồ sơ nghề nghiệp.' : 'Thêm hồ sơ nghề nghiệp.'}
      </p>
      <TextField
        label="Chức danh / nghề nghiệp"
        required
        maxLength={150}
        value={jobTitle}
        onChange={(event) => setJobTitle(event.target.value)}
      />
      <TextField
        label="Nơi làm việc"
        maxLength={200}
        value={company}
        onChange={(event) => setCompany(event.target.value)}
      />
      <TextField
        label="Địa điểm làm việc"
        maxLength={200}
        value={workLocation}
        onChange={(event) => setWorkLocation(event.target.value)}
      />
      {mutation.error instanceof Error && (
        <p role="alert" className="text-body text-error">
          {mutation.error.message}
        </p>
      )}
      {mutation.isSuccess && (
        <p role="status" className="text-body text-success">
          Đã lưu hồ sơ.
        </p>
      )}
      <Button type="submit" loading={mutation.isPending} fullWidth={false}>
        Lưu nghề nghiệp
      </Button>
    </form>
  );
}

function EducationPanel({
  personId,
  profile,
  loading,
}: {
  personId: string;
  profile: EducationProfile | null;
  loading: boolean;
}) {
  const queryClient = useQueryClient();
  const [school, setSchool] = useState(profile?.school ?? '');
  const [degree, setDegree] = useState(profile?.degree ?? '');
  const [graduationYear, setGraduationYear] = useState(
    profile?.graduationYear == null ? '' : String(profile.graduationYear),
  );
  useEffect(() => {
    setSchool(profile?.school ?? '');
    setDegree(profile?.degree ?? '');
    setGraduationYear(profile?.graduationYear == null ? '' : String(profile.graduationYear));
  }, [profile]);
  const mutation = useMutation({
    mutationFn: () =>
      directoryApi.saveEducationProfile(personId, {
        school: school.trim(),
        degree: degree.trim() || null,
        graduationYear: graduationYear ? Number(graduationYear) : null,
      }),
    onSuccess: async (saved) => {
      queryClient.setQueryData(['directory-education-profile', personId], saved);
      await queryClient.invalidateQueries({ queryKey: ['directory-members'] });
    },
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    mutation.mutate();
  }

  if (loading) return <p className="mt-6 text-body text-text-secondary">Đang tải hồ sơ...</p>;
  return (
    <form
      onSubmit={submit}
      className="mt-6 grid gap-4 rounded-card border border-border bg-surface p-5"
    >
      <p className="text-body text-text-secondary">
        {profile ? 'Cập nhật hồ sơ học vấn.' : 'Thêm hồ sơ học vấn.'}
      </p>
      <TextField
        label="Trường học"
        required
        maxLength={200}
        value={school}
        onChange={(event) => setSchool(event.target.value)}
      />
      <TextField
        label="Bậc học / bằng cấp"
        maxLength={100}
        value={degree}
        onChange={(event) => setDegree(event.target.value)}
      />
      <TextField
        label="Năm tốt nghiệp (không bắt buộc)"
        type="number"
        min={1900}
        max={new Date().getFullYear() + 10}
        value={graduationYear}
        onChange={(event) => setGraduationYear(event.target.value)}
      />
      {mutation.error instanceof Error && (
        <p role="alert" className="text-body text-error">
          {mutation.error.message}
        </p>
      )}
      {mutation.isSuccess && (
        <p role="status" className="text-body text-success">
          Đã lưu hồ sơ.
        </p>
      )}
      <Button type="submit" loading={mutation.isPending} fullWidth={false}>
        Lưu học vấn
      </Button>
    </form>
  );
}
