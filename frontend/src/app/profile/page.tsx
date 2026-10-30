'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { applyServerError, type FormAlert } from '@/components/auth/form-errors';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { Toast } from '@/components/ui/Toast';
import { ApiError } from '@/lib/api';
import { changePassword, getMyProfile, updateMyProfile } from '@/services/auth';

// UC-AUTH-05 (xem, sửa hồ sơ) và UC-AUTH-04 (đổi mật khẩu). Ràng buộc khớp UpdateProfileRequest,
// ChangePasswordRequest trong auth.yaml; backend kiểm tra lại (BR-AUTH-03: đủ 16 tuổi).
const MIN_AGE = 16;

function isAtLeast(dateOfBirth: string, years: number): boolean {
  const dob = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(dob.getTime())) return false;
  const limit = new Date(dob);
  limit.setFullYear(dob.getFullYear() + years);
  return limit <= new Date();
}

const profileSchema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, 'Họ tên không được để trống')
    .max(150, 'Họ tên tối đa 150 ký tự'),
  phone: z
    .string()
    .trim()
    .refine(
      (v) => v === '' || /^(\+84|0)[0-9]{9,10}$/.test(v),
      'Số điện thoại không đúng định dạng',
    ),
  dateOfBirth: z
    .string()
    .min(1, 'Ngày sinh không được để trống')
    .refine((v) => isAtLeast(v, MIN_AGE), `Bạn phải đủ ${MIN_AGE} tuổi`),
});
type ProfileValues = z.infer<typeof profileSchema>;
const PROFILE_FIELDS = ['fullName', 'phone', 'dateOfBirth'] as const;

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Mật khẩu hiện tại không được để trống'),
    newPassword: z
      .string()
      .min(8, 'Mật khẩu mới từ 8 đến 72 ký tự')
      .max(72, 'Mật khẩu mới từ 8 đến 72 ký tự'),
    confirmPassword: z.string(),
  })
  .refine((v) => v.newPassword === v.confirmPassword, {
    path: ['confirmPassword'],
    message: 'Mật khẩu nhập lại không khớp',
  });
type PasswordValues = z.infer<typeof passwordSchema>;
const PASSWORD_FIELDS = ['currentPassword', 'newPassword'] as const;

type Alert = FormAlert & { variant: 'success' | 'error' };

function ProfileForm() {
  const queryClient = useQueryClient();
  const [alert, setAlert] = useState<Alert | null>(null);
  const profile = useQuery({ queryKey: ['my-profile'], queryFn: getMyProfile });
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<ProfileValues>({
    resolver: zodResolver(profileSchema),
    defaultValues: { fullName: '', phone: '', dateOfBirth: '' },
  });

  useEffect(() => {
    if (profile.data) {
      reset({
        fullName: profile.data.fullName,
        phone: profile.data.phone ?? '',
        dateOfBirth: profile.data.dateOfBirth,
      });
    }
  }, [profile.data, reset]);

  const save = useMutation({ mutationFn: updateMyProfile });

  const onSubmit = async (values: ProfileValues) => {
    setAlert(null);
    try {
      const user = await save.mutateAsync({
        ...values,
        phone: values.phone === '' ? null : values.phone,
      });
      queryClient.setQueryData(['my-profile'], user);
      // Header của AppShell hiện họ tên: làm mới để đổi theo.
      await queryClient.invalidateQueries({ queryKey: ['app-shell-profile'] });
      setAlert({ variant: 'success', title: 'Đã lưu', message: 'Hồ sơ đã được cập nhật.' });
    } catch (error) {
      setAlert({ variant: 'error', ...applyServerError(error, PROFILE_FIELDS, setError) });
    }
  };

  return (
    <Card title="Hồ sơ cá nhân" description="Email dùng để đăng nhập, không đổi được.">
      {profile.isLoading && (
        <p role="status" className="text-body text-text-secondary">
          Đang tải hồ sơ...
        </p>
      )}
      {profile.error && (
        <div className="flex flex-col items-start gap-3">
          <Toast
            variant="error"
            title={(profile.error as ApiError).code ?? 'SERVER_001'}
            message={(profile.error as Error).message}
          />
          <Button variant="secondary" onClick={() => profile.refetch()}>
            Thử lại
          </Button>
        </div>
      )}
      {profile.data && (
        <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
          {alert && (
            <Toast
              variant={alert.variant}
              title={alert.title}
              message={alert.message}
              onClose={() => setAlert(null)}
            />
          )}
          <TextField label="Email" type="email" value={profile.data.email} readOnly disabled />
          <TextField
            label="Họ tên"
            autoComplete="name"
            error={errors.fullName?.message}
            {...register('fullName')}
          />
          <div className="grid gap-4 md:grid-cols-2 md:items-start">
            <TextField
              label="Ngày sinh"
              type="date"
              autoComplete="bday"
              error={errors.dateOfBirth?.message}
              {...register('dateOfBirth')}
            />
            <TextField
              label="Số điện thoại (tùy chọn)"
              type="tel"
              autoComplete="tel"
              error={errors.phone?.message}
              {...register('phone')}
            />
          </div>
          <div>
            <Button type="submit" disabled={isSubmitting || !isDirty}>
              Lưu thay đổi
            </Button>
          </div>
        </form>
      )}
    </Card>
  );
}

function ChangePasswordForm() {
  const router = useRouter();
  const [alert, setAlert] = useState<Alert | null>(null);
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<PasswordValues>({
    resolver: zodResolver(passwordSchema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = async ({ currentPassword, newPassword }: PasswordValues) => {
    setAlert(null);
    try {
      await changePassword({ currentPassword, newPassword });
      // UC-AUTH-04 bước 4: đổi xong phải đăng nhập lại.
      router.replace('/login?reason=password-changed');
    } catch (error) {
      setAlert({ variant: 'error', ...applyServerError(error, PASSWORD_FIELDS, setError) });
    }
  };

  return (
    <Card
      id="doi-mat-khau"
      title="Đổi mật khẩu"
      description="Đổi xong bạn cần đăng nhập lại trên mọi thiết bị."
    >
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex flex-col gap-4">
        {alert && (
          <Toast
            variant={alert.variant}
            title={alert.title}
            message={alert.message}
            onClose={() => setAlert(null)}
          />
        )}
        <TextField
          label="Mật khẩu hiện tại"
          type="password"
          autoComplete="current-password"
          error={errors.currentPassword?.message}
          {...register('currentPassword')}
        />
        <div className="grid gap-4 md:grid-cols-2 md:items-start">
          <TextField
            label="Mật khẩu mới"
            type="password"
            autoComplete="new-password"
            error={errors.newPassword?.message}
            {...register('newPassword')}
          />
          <TextField
            label="Nhập lại mật khẩu mới"
            type="password"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
            {...register('confirmPassword')}
          />
        </div>
        <div>
          <Button type="submit" disabled={isSubmitting}>
            Đổi mật khẩu
          </Button>
        </div>
      </form>
    </Card>
  );
}

export default function ProfilePage() {
  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
      <ProfileForm />
      <ChangePasswordForm />
    </div>
  );
}
