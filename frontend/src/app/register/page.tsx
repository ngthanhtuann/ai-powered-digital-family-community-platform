'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { AuthLayout } from '@/components/auth/AuthLayout';
import { applyServerError, type FormAlert } from '@/components/auth/form-errors';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { Toast } from '@/components/ui/Toast';
import { ApiError } from '@/lib/api';
import { register as registerUser } from '@/services/auth';

// UC-AUTH-01 Đăng ký, theo Figma page "02 Auth và Admin" (khung 360 / 768 / 1440 và "Đăng ký — trạng thái").
// Ràng buộc khớp RegisterRequest trong auth.yaml; backend kiểm tra lại (BR-AUTH-01 đến 03).
const MIN_AGE = 16;

function isAtLeast(dateOfBirth: string, years: number): boolean {
  const dob = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(dob.getTime())) return false;
  const limit = new Date(dob);
  limit.setFullYear(dob.getFullYear() + years);
  return limit <= new Date();
}

const schema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, 'Họ tên không được để trống')
    .max(150, 'Họ tên tối đa 150 ký tự'),
  email: z
    .string()
    .trim()
    .min(1, 'Email không được để trống')
    .max(255, 'Email tối đa 255 ký tự')
    .email('Email không đúng định dạng'),
  password: z.string().min(8, 'Mật khẩu từ 8 đến 72 ký tự').max(72, 'Mật khẩu từ 8 đến 72 ký tự'),
  dateOfBirth: z
    .string()
    .min(1, 'Ngày sinh không được để trống')
    .refine((v) => isAtLeast(v, MIN_AGE), `Bạn phải đủ ${MIN_AGE} tuổi để đăng ký`),
  phone: z
    .string()
    .trim()
    .refine(
      (v) => v === '' || /^(\+84|0)[0-9]{9,10}$/.test(v),
      'Số điện thoại không đúng định dạng',
    ),
});
type FormValues = z.infer<typeof schema>;
const FIELDS = ['fullName', 'email', 'password', 'dateOfBirth', 'phone'] as const;

export default function RegisterPage() {
  const router = useRouter();
  const [alert, setAlert] = useState<FormAlert | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', email: '', password: '', dateOfBirth: '', phone: '' },
  });

  const onSubmit = async (values: FormValues) => {
    setAlert(null);
    try {
      await registerUser({ ...values, phone: values.phone === '' ? null : values.phone });
      // 201: không tự đăng nhập, chuyển sang màn Đăng nhập.
      router.push('/login?registered=1');
    } catch (error) {
      if (error instanceof ApiError && error.code === 'AUTH_004') {
        setError('email', { type: 'server', message: error.message });
      }
      setAlert(applyServerError(error, FIELDS, setError));
    }
  };

  return (
    <AuthLayout
      title="Tạo tài khoản"
      description="Đăng ký để xin tham gia gia đình hoặc lập gia đình mới."
      switchPrompt="Đã có tài khoản?"
      switchHref="/login"
      switchLabel="Đăng nhập"
    >
      {alert && (
        <Toast
          variant="error"
          title={alert.title}
          message={alert.message}
          onClose={() => setAlert(null)}
        />
      )}
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex w-full flex-col gap-4">
        <TextField
          label="Họ tên"
          autoComplete="name"
          error={errors.fullName?.message}
          {...register('fullName')}
        />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          error={errors.email?.message}
          {...register('email')}
        />
        <TextField
          label="Mật khẩu"
          type="password"
          autoComplete="new-password"
          error={errors.password?.message}
          {...register('password')}
        />
        {/* Từ 768px: ngày sinh và điện thoại xếp một hàng (Figma v2). */}
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
        <p className="text-caption text-text-secondary">
          Mật khẩu 8–72 ký tự. Bạn cần đủ {MIN_AGE} tuổi để đăng ký.
        </p>
        <Button type="submit" disabled={isSubmitting}>
          Tạo tài khoản
        </Button>
      </form>
    </AuthLayout>
  );
}
