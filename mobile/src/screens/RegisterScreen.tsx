// UC-AUTH-01 Đăng ký. Figma mobile chưa có khung này (mobile.md mục 5): dựng theo bản web và design system.
import { zodResolver } from '@hookform/resolvers/zod';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Text } from 'react-native';
import { z } from 'zod';
import { applyServerError, isAtLeast, MIN_AGE, PHONE_PATTERN } from '../components/form-errors';
import { Button, Card, Notice, Screen, TextField, type NoticeMessage } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { register } from '../services/auth';
import { colors, text } from '../theme';

// Ràng buộc khớp RegisterRequest trong auth.yaml; backend kiểm tra lại (BR-AUTH-01 đến 03).
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
    .trim()
    .min(1, 'Ngày sinh không được để trống')
    .refine(
      (v) => isAtLeast(v, MIN_AGE),
      `Ngày sinh dạng YYYY-MM-DD và bạn phải đủ ${MIN_AGE} tuổi`,
    ),
  phone: z
    .string()
    .trim()
    .refine((v) => v === '' || PHONE_PATTERN.test(v), 'Số điện thoại không đúng định dạng'),
});
type FormValues = z.infer<typeof schema>;
const FIELDS = ['fullName', 'email', 'password', 'dateOfBirth', 'phone'] as const;

export function RegisterScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'Register'>) {
  const [alert, setAlert] = useState<NoticeMessage | null>(null);
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { fullName: '', email: '', password: '', dateOfBirth: '', phone: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setAlert(null);
    try {
      await register({ ...values, phone: values.phone === '' ? null : values.phone });
      // 201: không tự đăng nhập, về màn Đăng nhập.
      navigation.navigate('Login', { registered: true });
    } catch (error) {
      if (error instanceof ApiError && error.code === 'AUTH_004') {
        setError('email', { type: 'server', message: error.message });
      }
      setAlert(applyServerError(error, FIELDS, setError));
    }
  });

  const field = (name: (typeof FIELDS)[number], label: string, extra: object = {}) => (
    <Controller
      control={control}
      name={name}
      render={({ field: f }) => (
        <TextField
          label={label}
          value={f.value}
          onChangeText={f.onChange}
          onBlur={f.onBlur}
          error={errors[name]?.message}
          {...extra}
        />
      )}
    />
  );

  return (
    <Screen>
      <Card
        title="Tạo tài khoản"
        description="Đăng ký để xin tham gia gia đình hoặc lập gia đình mới."
      >
        {alert ? <Notice {...alert} /> : null}
        {field('fullName', 'Họ tên', { autoComplete: 'name' })}
        {field('email', 'Email', {
          keyboardType: 'email-address',
          autoCapitalize: 'none',
          autoComplete: 'email',
        })}
        {field('password', 'Mật khẩu', { secureTextEntry: true, autoComplete: 'new-password' })}
        {field('dateOfBirth', 'Ngày sinh (YYYY-MM-DD)', {
          placeholder: '1990-05-12',
          keyboardType: 'numbers-and-punctuation',
        })}
        {field('phone', 'Số điện thoại (tùy chọn)', {
          keyboardType: 'phone-pad',
          autoComplete: 'tel',
        })}
        <Text style={[text.caption, { color: colors.textSecondary }]}>
          Mật khẩu 8–72 ký tự. Bạn cần đủ {MIN_AGE} tuổi để đăng ký.
        </Text>
        <Button title="Tạo tài khoản" onPress={onSubmit} loading={isSubmitting} />
      </Card>
    </Screen>
  );
}
