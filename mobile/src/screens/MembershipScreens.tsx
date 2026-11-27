// UC-AUTH-06, UC-GEN-01, UC-AUTH-15: Tham gia gia đình, Chờ duyệt, Quên mật khẩu (như web join-family, pending-approval,
// forgot-password). Đổi trạng thái thì gọi lại membership để RootNavigator chuyển nhánh (mobile.md mục 3.2).
import { zodResolver } from '@hookform/resolvers/zod';
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation } from '@tanstack/react-query';
import { useEffect, useState, type ComponentProps } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Text } from 'react-native';
import { z } from 'zod';
import { applyServerError } from '../components/form-errors';
import { ErrorText, TextArea, confirmAction } from '../components/form-ui';
import { Button, Card, Notice, Screen, TextField, type NoticeMessage } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { joinRequestsApi } from '../services/admin';
import { resetPassword } from '../services/auth';
import { genealogyManageApi } from '../services/genealogy';
import { useSession } from '../session/SessionContext';
import { colors, text } from '../theme';

function AccountActions({ onProfile }: { onProfile: () => void }) {
  const { signOut } = useSession();
  return (
    <>
      <Button title="Hồ sơ cá nhân" variant="secondary" onPress={onProfile} />
      <Button title="Đăng xuất" variant="secondary" onPress={() => signOut()} />
    </>
  );
}

const joinSchema = z.object({
  code: z.string().trim().uuid('Mã gồm 36 ký tự, ví dụ 3f2a8c1e-0b7d-4e55-9a31-5d2c6b8e1a01'),
  message: z.string().trim().max(255, 'Tối đa 255 ký tự'),
});
type JoinValues = z.infer<typeof joinSchema>;
const createSchema = z.object({
  name: z.string().trim().min(1, 'Nhập tên gia đình').max(150, 'Tối đa 150 ký tự'),
});
type CreateValues = z.infer<typeof createSchema>;

/** UC-AUTH-06 bước 1, 2: nhập mã gia đình hoặc mã chi để xin tham gia, hoặc tự lập gia đình mới (UC-GEN-01). */
export function JoinFamilyScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'JoinFamily'>) {
  const { session, reloadMembership } = useSession();
  const membership = session.status === 'signedIn' ? session.membership : null;
  const joinForm = useForm<JoinValues>({
    resolver: zodResolver(joinSchema),
    defaultValues: { code: '', message: '' },
  });
  const join = useMutation({
    mutationFn: (v: JoinValues) =>
      joinRequestsApi.create({ code: v.code.trim(), message: v.message.trim() || null }),
    onSuccess: () => reloadMembership(),
  });
  const createForm = useForm<CreateValues>({
    resolver: zodResolver(createSchema),
    defaultValues: { name: '' },
  });
  // Người lập gia đình thành trưởng họ (BR-AUTH-12); tải lại membership để vào ứng dụng với vai trò mới.
  const create = useMutation({
    mutationFn: (v: CreateValues) => genealogyManageApi.createFamily({ name: v.name.trim() }),
    onSuccess: () => reloadMembership(),
  });
  return (
    <Screen>
      <Text style={[text.body, { color: colors.textSecondary }]}>
        Bạn chưa thuộc gia đình nào. Nhập mã gia đình hoặc mã chi do người thân, trưởng họ hoặc
        trưởng chi gửi, hoặc lập gia đình mới.
      </Text>
      {membership?.status === 'REJECTED' ? (
        <Notice
          variant="warning"
          title="Yêu cầu trước bị từ chối"
          message={`${membership.rejectReason ? `Lý do: ${membership.rejectReason}. ` : ''}Bạn có thể gửi yêu cầu mới.`}
        />
      ) : null}
      <Card
        title="Nhập mã để tham gia"
        description="Mã chi đưa yêu cầu tới trưởng chi; mã gia đình tới trưởng họ."
      >
        <Controller
          control={joinForm.control}
          name="code"
          render={({ field }) => (
            <TextField
              label="Mã gia đình hoặc mã chi"
              placeholder="Dán mã được gửi cho bạn"
              autoCapitalize="none"
              autoCorrect={false}
              value={field.value}
              onChangeText={field.onChange}
              error={joinForm.formState.errors.code?.message}
            />
          )}
        />
        <Controller
          control={joinForm.control}
          name="message"
          render={({ field }) => (
            <TextArea
              label="Lời nhắn cho người duyệt (không bắt buộc)"
              maxLength={255}
              placeholder="Ví dụ: con gái ông Nguyễn Văn Bình và bà Trần Thị Mai, chi trưởng"
              value={field.value}
              onChangeText={field.onChange}
              error={joinForm.formState.errors.message?.message}
            />
          )}
        />
        <Text style={[text.caption, { color: colors.textSecondary }]}>
          Ghi tên cha mẹ để trưởng họ tìm đúng bạn trên cây gia phả khi có người trùng tên.
        </Text>
        <ErrorText error={join.error} />
        <Button
          title="Gửi yêu cầu"
          loading={join.isPending}
          onPress={joinForm.handleSubmit((v) => join.mutate(v))}
        />
      </Card>
      <Card
        title="Lập gia đình mới"
        description="Bạn sẽ là trưởng họ và có thể mời người thân tham gia."
      >
        <Controller
          control={createForm.control}
          name="name"
          render={({ field }) => (
            <TextField
              label="Tên gia đình"
              placeholder="Ví dụ: Dòng họ Nguyễn"
              maxLength={150}
              value={field.value}
              onChangeText={field.onChange}
              error={createForm.formState.errors.name?.message}
            />
          )}
        />
        <ErrorText error={create.error} />
        <Button
          title="Lập gia đình"
          variant="secondary"
          loading={create.isPending}
          onPress={createForm.handleSubmit((v) => create.mutate(v))}
        />
      </Card>
      <AccountActions onProfile={() => navigation.navigate('Profile')} />
    </Screen>
  );
}

/** UC-AUTH-06 bước 5, 6: chờ duyệt, tự hỏi lại trạng thái mỗi 30 giây; hủy yêu cầu. */
export function PendingApprovalScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'PendingApproval'>) {
  const { session, reloadMembership } = useSession();
  const membership = session.status === 'signedIn' ? session.membership : null;
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState<unknown>(null);
  const check = async () => {
    setChecking(true);
    setError(null);
    try {
      await reloadMembership();
    } catch (e) {
      setError(e);
    } finally {
      setChecking(false);
    }
  };
  useEffect(() => {
    const timer = setInterval(() => {
      reloadMembership().catch(() => undefined); // lỗi mạng tạm thời: lần sau thử lại
    }, 30_000);
    return () => clearInterval(timer);
  }, [reloadMembership]);
  const cancel = useMutation({
    mutationFn: () => joinRequestsApi.cancel(membership!.joinRequestId!),
    onSuccess: () => reloadMembership(),
  });
  return (
    <Screen>
      <Card title="Tài khoản đang chờ duyệt">
        <Text style={[text.body, { color: colors.textPrimary }]}>
          Bạn đã xin tham gia {membership?.familyName ?? 'gia đình'}
          {membership?.branchName ? ` (${membership.branchName})` : ''}.{' '}
          {membership?.branchName ? 'Trưởng chi hoặc trưởng họ' : 'Trưởng họ'} sẽ duyệt yêu cầu; màn
          này tự cập nhật.
        </Text>
        <Text style={[text.caption, { color: colors.textSecondary }]}>
          Trong lúc chờ, bạn vẫn xem và sửa được hồ sơ cá nhân.
        </Text>
        <ErrorText error={error ?? cancel.error} />
        <Button title="Kiểm tra lại" variant="secondary" loading={checking} onPress={check} />
        <Button
          title="Hủy yêu cầu"
          variant="danger"
          loading={cancel.isPending}
          disabled={!membership?.joinRequestId}
          onPress={() =>
            confirmAction(
              'Hủy yêu cầu',
              'Hủy yêu cầu tham gia này?',
              () => cancel.mutate(),
              'Hủy yêu cầu',
            )
          }
        />
        <AccountActions onProfile={() => navigation.navigate('Profile')} />
      </Card>
    </Screen>
  );
}

// UC-AUTH-15 (SCRUM-139): không có email, nên mã do trưởng họ hoặc quản trị cấp.
const resetSchema = z
  .object({
    email: z
      .string()
      .trim()
      .min(1, 'Email không được để trống')
      .email('Email không đúng định dạng'),
    code: z.string().trim().min(1, 'Mã không được để trống').max(20, 'Mã tối đa 20 ký tự'),
    newPassword: z
      .string()
      .min(8, 'Mật khẩu mới từ 8 đến 72 ký tự')
      .max(72, 'Mật khẩu mới từ 8 đến 72 ký tự'),
    confirm: z.string(),
  })
  .refine((v) => v.newPassword === v.confirm, {
    path: ['confirm'],
    message: 'Mật khẩu nhập lại không khớp',
  });
type ResetValues = z.infer<typeof resetSchema>;
const RESET_FIELDS = ['email', 'code', 'newPassword'] as const;

export function ForgotPasswordScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'ForgotPassword'>) {
  const [alert, setAlert] = useState<NoticeMessage | null>(null);
  const {
    control,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<ResetValues>({
    resolver: zodResolver(resetSchema),
    defaultValues: { email: '', code: '', newPassword: '', confirm: '' },
  });
  const onSubmit = handleSubmit(async ({ email, code, newPassword }) => {
    setAlert(null);
    try {
      await resetPassword({ email: email.trim(), code: code.trim(), newPassword });
      navigation.replace('Login', { passwordReset: true });
    } catch (error) {
      setAlert(applyServerError(error, RESET_FIELDS, setError));
    }
  });
  const input = (
    name: keyof ResetValues,
    label: string,
    extra: Partial<ComponentProps<typeof TextField>> = {},
  ) => (
    <Controller
      control={control}
      name={name}
      render={({ field }) => (
        <TextField
          label={label}
          value={field.value}
          onChangeText={field.onChange}
          onBlur={field.onBlur}
          error={errors[name]?.message}
          {...extra}
        />
      )}
    />
  );
  return (
    <Screen>
      <Card
        title="Quên mật khẩu"
        description="Nhờ trưởng họ (hoặc quản trị) cấp mã đặt lại mật khẩu, rồi nhập mã cùng mật khẩu mới ở đây. Mã dùng một lần, hết hạn sau 30 phút."
      >
        {alert ? <Notice {...alert} /> : null}
        {input('email', 'Email', {
          keyboardType: 'email-address',
          autoCapitalize: 'none',
          autoComplete: 'email',
        })}
        {input('code', 'Mã đặt lại', { autoCapitalize: 'characters', autoCorrect: false })}
        {input('newPassword', 'Mật khẩu mới', {
          secureTextEntry: true,
          autoComplete: 'new-password',
        })}
        {input('confirm', 'Nhập lại mật khẩu mới', {
          secureTextEntry: true,
          autoComplete: 'new-password',
        })}
        <Button title="Đặt lại mật khẩu" onPress={onSubmit} loading={isSubmitting} />
      </Card>
    </Screen>
  );
}
