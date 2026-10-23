'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useRouter, useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { AuthLayout } from '@/components/auth/AuthLayout';
import { applyServerError, type FormAlert } from '@/components/auth/form-errors';
import { Button } from '@/components/ui/Button';
import { TextField } from '@/components/ui/TextField';
import { Toast } from '@/components/ui/Toast';
import { ApiError } from '@/lib/api';
import { login, routeAfterLogin } from '@/services/auth';

// UC-AUTH-02 Đăng nhập, theo Figma page "02 Auth và Admin" (khung 360 / 768 / 1440 và "Đăng nhập — trạng thái").
const schema = z.object({
  email: z.string().trim().min(1, 'Email không được để trống').email('Email không đúng định dạng'),
  password: z.string().min(1, 'Mật khẩu không được để trống'),
});
type FormValues = z.infer<typeof schema>;
const FIELDS = ['email', 'password'] as const;

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const [alert, setAlert] = useState<(FormAlert & { variant: 'success' | 'error' }) | null>(null);
  const [waitUntil, setWaitUntil] = useState<number | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });

  // Thông báo khi vừa đăng ký xong hoặc bị đẩy ra vì tài khoản bị khóa.
  useEffect(() => {
    if (searchParams.get('registered') === '1') {
      setAlert({ variant: 'success', title: 'Đã tạo tài khoản', message: 'Mời bạn đăng nhập.' });
    } else if (searchParams.get('reason') === 'locked') {
      setAlert({ variant: 'error', title: 'AUTH_003', message: 'Tài khoản đã bị khóa' });
    }
  }, [searchParams]);

  // RATE_001: khóa nút đến hết thời gian Retry-After, không tự đặt thời gian khác.
  useEffect(() => {
    if (!waitUntil) return;
    const timer = setTimeout(() => setWaitUntil(null), Math.max(0, waitUntil - Date.now()));
    return () => clearTimeout(timer);
  }, [waitUntil]);

  const onSubmit = async (values: FormValues) => {
    setAlert(null);
    try {
      const data = await login(values);
      router.replace(routeAfterLogin(data, searchParams.get('next')));
    } catch (error) {
      if (error instanceof ApiError && error.code === 'RATE_001' && error.retryAfter) {
        setWaitUntil(Date.now() + error.retryAfter * 1000);
      }
      setAlert({ variant: 'error', ...applyServerError(error, FIELDS, setError) });
    }
  };

  return (
    <AuthLayout
      title="Đăng nhập"
      description="Chào mừng bạn quay lại. Đăng nhập để xem gia đình của bạn."
      switchPrompt="Chưa có tài khoản?"
      switchHref="/register"
      switchLabel="Đăng ký ngay"
    >
      {alert && (
        <Toast
          variant={alert.variant}
          title={alert.title}
          message={alert.message}
          onClose={() => setAlert(null)}
        />
      )}
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex w-full flex-col gap-4">
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
          autoComplete="current-password"
          error={errors.password?.message}
          {...register('password')}
        />
        <Button type="submit" disabled={isSubmitting || waitUntil !== null}>
          Đăng nhập
        </Button>
      </form>
    </AuthLayout>
  );
}

export default function LoginPage() {
  // useSearchParams cần Suspense khi Next.js dựng trang tĩnh.
  return (
    <Suspense>
      <LoginForm />
    </Suspense>
  );
}
