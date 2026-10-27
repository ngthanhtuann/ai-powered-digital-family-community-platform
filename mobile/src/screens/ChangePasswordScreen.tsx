// UC-AUTH-04 Đổi mật khẩu. Đổi xong server thu hồi refresh token nên phải đăng nhập lại.
import { zodResolver } from '@hookform/resolvers/zod';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { applyServerError } from '../components/form-errors';
import { Button, Card, Notice, Screen, TextField, type NoticeMessage } from '../components/ui';
import { changePassword } from '../services/auth';
import { useSession } from '../session/SessionContext';

const schema = z
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
type FormValues = z.infer<typeof schema>;
const FIELDS = ['currentPassword', 'newPassword', 'confirmPassword'] as const;
const LABELS: Record<(typeof FIELDS)[number], string> = {
  currentPassword: 'Mật khẩu hiện tại',
  newPassword: 'Mật khẩu mới',
  confirmPassword: 'Nhập lại mật khẩu mới',
};

export function ChangePasswordScreen() {
  const { endSession } = useSession();
  const [alert, setAlert] = useState<NoticeMessage | null>(null);
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { currentPassword: '', newPassword: '', confirmPassword: '' },
  });

  const onSubmit = handleSubmit(async ({ currentPassword, newPassword }) => {
    setAlert(null);
    try {
      await changePassword({ currentPassword, newPassword });
      endSession({
        variant: 'success',
        title: 'Đã đổi mật khẩu',
        message: 'Mời bạn đăng nhập lại.',
      });
    } catch (error) {
      setAlert(applyServerError(error, FIELDS, setError));
    }
  });

  return (
    <Screen>
      <Card title="Đổi mật khẩu" description="Đổi xong bạn cần đăng nhập lại trên mọi thiết bị.">
        {alert ? <Notice {...alert} /> : null}
        {FIELDS.map((name) => (
          <Controller
            key={name}
            control={control}
            name={name}
            render={({ field }) => (
              <TextField
                label={LABELS[name]}
                secureTextEntry
                autoComplete={name === 'currentPassword' ? 'current-password' : 'new-password'}
                value={field.value}
                onChangeText={field.onChange}
                onBlur={field.onBlur}
                error={errors[name]?.message}
              />
            )}
          />
        ))}
        <Button title="Đổi mật khẩu" onPress={onSubmit} loading={isSubmitting} />
      </Card>
    </Screen>
  );
}
