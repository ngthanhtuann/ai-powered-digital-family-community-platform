// UC-AUTH-06: màn Nhập mã gia đình và Chờ duyệt (mobile.md mục 3.2).
// Chỉ là khung điều hướng: API gửi, hủy yêu cầu tham gia (POST/DELETE /join-requests) và tạo gia đình
// trên mobile làm ở task khác, nên ở đây chỉ hiện trạng thái và cho làm mới.
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useState } from 'react';
import { Text } from 'react-native';
import { Button, Card, Notice, Screen } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
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

function useReload() {
  const { reloadMembership } = useSession();
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const reload = async () => {
    setLoading(true);
    setError(null);
    try {
      await reloadMembership();
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  };
  return { reload, loading, error };
}

export function JoinFamilyScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'JoinFamily'>) {
  const { session } = useSession();
  const { reload, loading, error } = useReload();
  const membership = session.status === 'signedIn' ? session.membership : null;
  return (
    <Screen>
      <Card
        title="Tham gia gia đình"
        description="Bạn chưa thuộc gia đình nào. Hãy nhập mã gia đình do người thân cung cấp, hoặc tạo gia đình mới."
      >
        {membership?.status === 'REJECTED' ? (
          <Notice
            variant="warning"
            title="Yêu cầu bị từ chối"
            message={membership.rejectReason ?? 'Bạn có thể gửi yêu cầu mới.'}
          />
        ) : null}
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Chức năng gửi mã gia đình và tạo gia đình trên ứng dụng sẽ có ở bản sau. Hiện bạn làm trên
          web, rồi bấm “Kiểm tra lại”.
        </Text>
        {error ? <Notice variant="error" title="Lỗi" message={error} /> : null}
        <Button title="Kiểm tra lại" onPress={reload} loading={loading} />
        <AccountActions onProfile={() => navigation.navigate('Profile')} />
      </Card>
    </Screen>
  );
}

export function PendingApprovalScreen({
  navigation,
}: NativeStackScreenProps<RootStackParamList, 'PendingApproval'>) {
  const { session } = useSession();
  const { reload, loading, error } = useReload();
  const membership = session.status === 'signedIn' ? session.membership : null;
  return (
    <Screen>
      <Card title="Đang chờ duyệt" description="Trưởng chi sẽ xem yêu cầu tham gia của bạn.">
        {membership?.familyName ? (
          <Text style={[text.body, { color: colors.textPrimary }]}>
            Gia đình: {membership.familyName}
            {membership.branchName ? ` · ${membership.branchName}` : ''}
          </Text>
        ) : null}
        {error ? <Notice variant="error" title="Lỗi" message={error} /> : null}
        <Button title="Làm mới" onPress={reload} loading={loading} />
        <AccountActions onProfile={() => navigation.navigate('Profile')} />
      </Card>
    </Screen>
  );
}
