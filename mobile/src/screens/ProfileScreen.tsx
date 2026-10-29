// UC-AUTH-05 Xem và sửa hồ sơ cá nhân. Figma mobile chưa có khung này (mobile.md mục 5).
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { z } from 'zod';
import { applyServerError, isAtLeast, MIN_AGE, PHONE_PATTERN } from '../components/form-errors';
import {
  Button,
  Card,
  Loading,
  Notice,
  Screen,
  TextField,
  type NoticeMessage,
} from '../components/ui';
import { ApiError } from '../services/api';
import { getMyProfile, updateMyProfile } from '../services/auth';
import { useSession } from '../session/SessionContext';

const schema = z.object({
  fullName: z
    .string()
    .trim()
    .min(1, 'Họ tên không được để trống')
    .max(150, 'Họ tên tối đa 150 ký tự'),
  phone: z
    .string()
    .trim()
    .refine((v) => v === '' || PHONE_PATTERN.test(v), 'Số điện thoại không đúng định dạng'),
  dateOfBirth: z
    .string()
    .trim()
    .refine(
      (v) => isAtLeast(v, MIN_AGE),
      `Ngày sinh dạng YYYY-MM-DD và bạn phải đủ ${MIN_AGE} tuổi`,
    ),
});
type FormValues = z.infer<typeof schema>;
const FIELDS = ['fullName', 'phone', 'dateOfBirth'] as const;

export function ProfileScreen() {
  const { setUser } = useSession();
  const queryClient = useQueryClient();
  const [alert, setAlert] = useState<NoticeMessage | null>(null);
  const profile = useQuery({ queryKey: ['my-profile'], queryFn: getMyProfile });
  const save = useMutation({ mutationFn: updateMyProfile });
  const {
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
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

  const onSubmit = handleSubmit(async (values) => {
    setAlert(null);
    try {
      // Không có mạng thì báo lỗi, không báo thành công giả (mobile.md mục 4.6).
      const user = await save.mutateAsync({
        ...values,
        phone: values.phone === '' ? null : values.phone,
      });
      queryClient.setQueryData(['my-profile'], user);
      setUser(user);
      setAlert({ variant: 'success', title: 'Đã lưu', message: 'Hồ sơ đã được cập nhật.' });
    } catch (error) {
      setAlert(applyServerError(error, FIELDS, setError));
    }
  });

  if (profile.isLoading) return <Loading label="Đang tải hồ sơ..." />;
  if (profile.error || !profile.data) {
    const err = profile.error as ApiError | null;
    return (
      <Screen>
        <Notice
          variant="error"
          title={err?.code ?? 'SERVER_001'}
          message={err?.message ?? 'Không tải được hồ sơ'}
        />
        <Button title="Thử lại" variant="secondary" onPress={() => profile.refetch()} />
      </Screen>
    );
  }

  return (
    <Screen>
      <Card title="Hồ sơ cá nhân" description="Email dùng để đăng nhập, không đổi được.">
        {alert ? <Notice {...alert} /> : null}
        <TextField label="Email" value={profile.data.email} editable={false} />
        <Controller
          control={control}
          name="fullName"
          render={({ field }) => (
            <TextField
              label="Họ tên"
              autoComplete="name"
              value={field.value}
              onChangeText={field.onChange}
              onBlur={field.onBlur}
              error={errors.fullName?.message}
            />
          )}
        />
        <Controller
          control={control}
          name="dateOfBirth"
          render={({ field }) => (
            <TextField
              label="Ngày sinh (YYYY-MM-DD)"
              keyboardType="numbers-and-punctuation"
              value={field.value}
              onChangeText={field.onChange}
              onBlur={field.onBlur}
              error={errors.dateOfBirth?.message}
            />
          )}
        />
        <Controller
          control={control}
          name="phone"
          render={({ field }) => (
            <TextField
              label="Số điện thoại (tùy chọn)"
              keyboardType="phone-pad"
              autoComplete="tel"
              value={field.value}
              onChangeText={field.onChange}
              onBlur={field.onBlur}
              error={errors.phone?.message}
            />
          )}
        />
        <Button
          title="Lưu thay đổi"
          onPress={onSubmit}
          loading={isSubmitting}
          disabled={!isDirty}
        />
      </Card>
    </Screen>
  );
}
