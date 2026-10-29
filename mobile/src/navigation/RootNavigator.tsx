// Stack gốc chọn nhánh theo trạng thái đăng nhập và membership (mobile.md mục 3.2).
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { View, Text } from 'react-native';
import { Button, Loading } from '../components/ui';
import { ChangePasswordScreen } from '../screens/ChangePasswordScreen';
import { JoinFamilyScreen, PendingApprovalScreen } from '../screens/MembershipScreens';
import { LoginScreen } from '../screens/LoginScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { RegisterScreen } from '../screens/RegisterScreen';
import { branchOf, useSession } from '../session/SessionContext';
import { colors, spacing, text } from '../theme';
import { AppDrawer } from './AppDrawer';
import type { RootStackParamList } from './types';

const Stack = createNativeStackNavigator<RootStackParamList>();

const headerOptions = {
  headerStyle: { backgroundColor: colors.surface },
  headerTintColor: colors.textPrimary,
  contentStyle: { backgroundColor: colors.background },
} as const;

export function RootNavigator() {
  const { session, retry } = useSession();

  if (session.status === 'loading') return <Loading />;
  if (session.status === 'error') {
    return (
      <View
        style={{
          flex: 1,
          justifyContent: 'center',
          padding: spacing.page,
          gap: 12,
          backgroundColor: colors.background,
        }}
      >
        <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>
          Không tải được dữ liệu
        </Text>
        <Text style={[text.body, { color: colors.textSecondary }]}>{session.message}</Text>
        <Button title="Thử lại" onPress={retry} />
      </View>
    );
  }

  const branch = session.status === 'signedIn' ? branchOf(session.user, session.membership) : null;

  return (
    <Stack.Navigator screenOptions={headerOptions}>
      {branch === null && (
        <>
          <Stack.Screen name="Login" component={LoginScreen} options={{ headerShown: false }} />
          <Stack.Screen name="Register" component={RegisterScreen} options={{ title: 'Đăng ký' }} />
        </>
      )}
      {branch === 'app' && (
        <Stack.Screen name="App" component={AppDrawer} options={{ headerShown: false }} />
      )}
      {(branch === 'join' || branch === 'pending') && (
        <>
          {branch === 'join' ? (
            <Stack.Screen
              name="JoinFamily"
              component={JoinFamilyScreen}
              options={{ title: 'Gia đình' }}
            />
          ) : (
            <Stack.Screen
              name="PendingApproval"
              component={PendingApprovalScreen}
              options={{ title: 'Chờ duyệt' }}
            />
          )}
          <Stack.Screen
            name="Profile"
            component={ProfileScreen}
            options={{ title: 'Hồ sơ cá nhân' }}
          />
          <Stack.Screen
            name="ChangePassword"
            component={ChangePasswordScreen}
            options={{ title: 'Đổi mật khẩu' }}
          />
        </>
      )}
    </Stack.Navigator>
  );
}
