// UC-AUTH-02 Đăng nhập (Figma page 06 `UC-AUTH-02 Đăng nhập / 360`).
import { zodResolver } from '@hookform/resolvers/zod';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Pressable, Text, View } from 'react-native';
import { z } from 'zod';
import { applyServerError } from '../components/form-errors';
import { Button, Card, Notice, Screen, TextField, type NoticeMessage } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { ApiError } from '../services/api';
import { login } from '../services/auth';
import { useSession } from '../session/SessionContext';
import { colors, text } from '../theme';

const schema = z.object({
  email: z.string().trim().min(1, 'Email không được để trống').email('Email không đúng định dạng'),
  password: z.string().min(1, 'Mật khẩu không được để trống'),
});
type FormValues = z.infer<typeof schema>;
const FIELDS = ['email', 'password'] as const;

export function LoginScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'Login'>) {
  const { session, signIn } = useSession();
  const sessionNotice = session.status === 'signedOut' ? session.notice : undefined;
  const [alert, setAlert] = useState<NoticeMessage | null>(null);
  const shown =
    alert ??
    (route.params?.registered
      ? { variant: 'success' as const, title: 'Đã tạo tài khoản', message: 'Mời bạn đăng nhập.' }
      : sessionNotice);

  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });

  const onSubmit = handleSubmit(async (values) => {
    setAlert(null);
    try {
      signIn(await login(values));
    } catch (error) {
      if (error instanceof ApiError && error.code === 'AUTH_001') {
        setAlert({ variant: 'error', title: 'AUTH_001', message: 'Sai email hoặc mật khẩu' });
      } else {
        setAlert(applyServerError(error, FIELDS, setError));
      }
    }
  });

  return (
    <Screen>
      <View style={{ paddingTop: 48, gap: 4 }}>
        <Text style={[text.pageTitle, { color: colors.textPrimary }]}>FamilyConnect</Text>
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Đăng nhập để kết nối với gia đình.
        </Text>
      </View>
      <Card title="Đăng nhập">
        {shown ? <Notice {...shown} /> : null}
        <Controller
          control={control}
          name="email"
          render={({ field }) => (
            <TextField
              label="Email"
              keyboardType="email-address"
              autoCapitalize="none"
              autoComplete="email"
              textContentType="emailAddress"
              value={field.value}
              onChangeText={field.onChange}
              onBlur={field.onBlur}
              error={errors.email?.message}
            />
          )}
        />
        <Controller
          control={control}
          name="password"
          render={({ field }) => (
            <TextField
              label="Mật khẩu"
              secureTextEntry
              autoComplete="current-password"
              textContentType="password"
              value={field.value}
              onChangeText={field.onChange}
              onBlur={field.onBlur}
              onSubmitEditing={() => onSubmit()}
              error={errors.password?.message}
            />
          )}
        />
        <Button title="Đăng nhập" onPress={onSubmit} loading={isSubmitting} />
      </Card>
      <Pressable
        accessibilityRole="link"
        onPress={() => navigation.navigate('Register')}
        style={{ minHeight: 44, justifyContent: 'center' }}
      >
        <Text style={[text.body, { color: colors.textSecondary, textAlign: 'center' }]}>
          Chưa có tài khoản?{' '}
          <Text style={{ color: colors.textPrimary, fontWeight: '600' }}>Đăng ký</Text>
        </Text>
      </Pressable>
    </Screen>
  );
}
