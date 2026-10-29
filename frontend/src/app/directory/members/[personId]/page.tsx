'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
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

const professionSchema = z.object({
  jobTitle: z.string().trim().min(1, 'Chức danh không được để trống').max(150, 'Tối đa 150 ký tự'),
  company: z.string().trim().max(200, 'Tối đa 200 ký tự'),
  workLocation: z.string().trim().max(200, 'Tối đa 200 ký tự'),
});
type ProfessionValues = z.infer<typeof professionSchema>;

const maxGraduationYear = () => new Date().getFullYear() + 10;
const educationSchema = z.object({
  school: z.string().trim().min(1, 'Trường học không được để trống').max(200, 'Tối đa 200 ký tự'),
  degree: z.string().trim().max(100, 'Tối đa 100 ký tự'),
  graduationYear: z
    .string()
    .trim()
    .refine((value) => value === '' || /^\d{4}$/.test(value), 'Năm gồm 4 chữ số')
    .refine(
      (value) => value === '' || (Number(value) >= 1900 && Number(value) <= maxGraduationYear()),
      'Năm tốt nghiệp từ 1900 đến năm hiện tại + 10',
    ),
});
type EducationValues = z.infer<typeof educationSchema>;

function professionDefaults(profile: ProfessionProfile | null): ProfessionValues {
  return {
    jobTitle: profile?.jobTitle ?? '',
    company: profile?.company ?? '',
    workLocation: profile?.workLocation ?? '',
  };
}

function educationDefaults(profile: EducationProfile | null): EducationValues {
  return {
    school: profile?.school ?? '',
    degree: profile?.degree ?? '',
    graduationYear: profile?.graduationYear == null ? '' : String(profile.graduationYear),
  };
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
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<ProfessionValues>({
    resolver: zodResolver(professionSchema),
    defaultValues: professionDefaults(profile),
  });
  useEffect(() => {
    reset(professionDefaults(profile));
  }, [profile, reset]);
  const mutation = useMutation({
    mutationFn: (values: ProfessionValues) =>
      directoryApi.saveProfessionProfile(personId, {
        jobTitle: values.jobTitle,
        company: values.company || null,
        workLocation: values.workLocation || null,
      }),
    onSuccess: async (saved) => {
      queryClient.setQueryData(['directory-profession-profile', personId], saved);
      await queryClient.invalidateQueries({ queryKey: ['directory-members'] });
    },
  });

  if (loading) return <p className="mt-6 text-body text-text-secondary">Đang tải hồ sơ...</p>;
  return (
    <form
      onSubmit={handleSubmit((values) => mutation.mutate(values))}
      noValidate
      className="mt-6 grid gap-4 rounded-card border border-border bg-surface p-5"
    >
      <p className="text-body text-text-secondary">
        {profile ? 'Cập nhật hồ sơ nghề nghiệp.' : 'Thêm hồ sơ nghề nghiệp.'}
      </p>
      <TextField
        label="Chức danh / nghề nghiệp"
        required
        maxLength={150}
        error={errors.jobTitle?.message}
        {...register('jobTitle')}
      />
      <TextField
        label="Nơi làm việc"
        maxLength={200}
        error={errors.company?.message}
        {...register('company')}
      />
      <TextField
        label="Địa điểm làm việc"
        maxLength={200}
        error={errors.workLocation?.message}
        {...register('workLocation')}
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
  const {
    register,
    handleSubmit,
    reset,
    formState: { errors },
  } = useForm<EducationValues>({
    resolver: zodResolver(educationSchema),
    defaultValues: educationDefaults(profile),
  });
  useEffect(() => {
    reset(educationDefaults(profile));
  }, [profile, reset]);
  const mutation = useMutation({
    mutationFn: (values: EducationValues) =>
      directoryApi.saveEducationProfile(personId, {
        school: values.school,
        degree: values.degree || null,
        graduationYear: values.graduationYear ? Number(values.graduationYear) : null,
      }),
    onSuccess: async (saved) => {
      queryClient.setQueryData(['directory-education-profile', personId], saved);
      await queryClient.invalidateQueries({ queryKey: ['directory-members'] });
    },
  });

  if (loading) return <p className="mt-6 text-body text-text-secondary">Đang tải hồ sơ...</p>;
  return (
    <form
      onSubmit={handleSubmit((values) => mutation.mutate(values))}
      noValidate
      className="mt-6 grid gap-4 rounded-card border border-border bg-surface p-5"
    >
      <p className="text-body text-text-secondary">
        {profile ? 'Cập nhật hồ sơ học vấn.' : 'Thêm hồ sơ học vấn.'}
      </p>
      <TextField
        label="Trường học"
        required
        maxLength={200}
        error={errors.school?.message}
        {...register('school')}
      />
      <TextField
        label="Bậc học / bằng cấp"
        maxLength={100}
        error={errors.degree?.message}
        {...register('degree')}
      />
      <TextField
        label="Năm tốt nghiệp (không bắt buộc)"
        inputMode="numeric"
        maxLength={4}
        error={errors.graduationYear?.message}
        {...register('graduationYear')}
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
