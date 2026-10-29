// Tổng quan (trang chủ trong menu drawer). Nội dung Tổng quan và các module khác thêm ở task sau.
import { Text } from 'react-native';
import { Card, Screen } from '../components/ui';
import { useSession } from '../session/SessionContext';
import { colors, text } from '../theme';

const ROLE_LABEL: Record<string, string> = {
  SYSTEM_ADMIN: 'Quản trị hệ thống',
  BRANCH_ADMIN: 'Trưởng chi',
  MEMBER: 'Thành viên',
};

export function HomeScreen() {
  const { session } = useSession();
  if (session.status !== 'signedIn') return null;
  const { user, membership } = session;
  const role = user.roles.includes('SYSTEM_ADMIN') ? 'SYSTEM_ADMIN' : (membership.role ?? 'MEMBER');
  return (
    <Screen>
      <Card title={`Xin chào, ${user.fullName}`}>
        {membership.familyName ? (
          <Text style={[text.body, { color: colors.textPrimary }]}>
            Gia đình: {membership.familyName}
          </Text>
        ) : null}
        <Text style={[text.body, { color: colors.textSecondary }]}>
          Vai trò: {ROLE_LABEL[role] ?? role}
        </Text>
      </Card>
    </Screen>
  );
}
