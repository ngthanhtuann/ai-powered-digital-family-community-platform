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
import { resetPassword } from '@/services/auth';

// UC-AUTH-15 (SCRUM-139): không có email, nên mã do trưởng họ hoặc quản trị cấp.
const schema = z.object({
  email: z.string().trim().min(1, 'Email không được để trống').email('Email không đúng định dạng'),
  code: z.string().trim().min(1, 'Mã không được để trống').max(20, 'Mã tối đa 20 ký tự'),
  newPassword: z.string().min(8, 'Mật khẩu mới từ 8 đến 72 ký tự').max(72, 'Mật khẩu mới từ 8 đến 72 ký tự'),
  confirm: z.string(),
}).refine((v) => v.newPassword === v.confirm, { path: ['confirm'], message: 'Mật khẩu nhập lại không khớp' });
type FormValues = z.infer<typeof schema>;
const FIELDS = ['email', 'code', 'newPassword'] as const;

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [alert, setAlert] = useState<FormAlert | null>(null);
  const { register, handleSubmit, setError, formState: { errors, isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', code: '', newPassword: '', confirm: '' },
  });

  const onSubmit = async ({ email, code, newPassword }: FormValues) => {
    setAlert(null);
    try {
      await resetPassword({ email, code, newPassword });
      router.replace('/login?reason=password-reset');
    } catch (error) {
      setAlert(applyServerError(error, FIELDS, setError));
    }
  };

  return (
    <AuthLayout
      title="Quên mật khẩu"
      description="Nhờ trưởng họ (hoặc quản trị) cấp mã đặt lại mật khẩu, rồi nhập mã cùng mật khẩu mới ở đây. Mã dùng một lần, hết hạn sau 30 phút."
      switchPrompt="Nhớ mật khẩu rồi?"
      switchHref="/login"
      switchLabel="Đăng nhập"
    >
      {alert && <Toast variant="error" title={alert.title} message={alert.message} onClose={() => setAlert(null)} />}
      <form onSubmit={handleSubmit(onSubmit)} noValidate className="flex w-full flex-col gap-4">
        <TextField label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <TextField label="Mã đặt lại" autoComplete="one-time-code" autoCapitalize="characters" error={errors.code?.message}
          {...register('code')} />
        <TextField label="Mật khẩu mới" type="password" autoComplete="new-password" error={errors.newPassword?.message}
          {...register('newPassword')} />
        <TextField label="Nhập lại mật khẩu mới" type="password" autoComplete="new-password" error={errors.confirm?.message}
          {...register('confirm')} />
        <Button type="submit" disabled={isSubmitting}>Đặt lại mật khẩu</Button>
      </form>
    </AuthLayout>
  );
}
