// Stack gốc chọn nhánh theo trạng thái đăng nhập và membership (mobile.md mục 3.2).
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { View, Text } from 'react-native';
import { Button, Loading } from '../components/ui';
import { ChangePasswordScreen } from '../screens/ChangePasswordScreen';
import {
  ForgotPasswordScreen,
  JoinFamilyScreen,
  PendingApprovalScreen,
} from '../screens/MembershipScreens';
import {
  DeathFormScreen,
  DeathReportFormScreen,
  DivorceScreen,
  PersonFormScreen,
  RelativeFormScreen,
  ReplaceAccountScreen,
  ResetCodeScreen,
} from '../screens/GenealogyManageScreens';
import {
  AccountRoleScreen,
  ApproveRequestScreen,
  BranchFormScreen,
  InviteScreen,
  PickBranchHeadScreen,
  RejectRequestScreen,
} from '../screens/ManageScreens';
import { AdminAssignScreen, AdminLockScreen } from '../screens/AdminScreens';
import {
  AnnouncementFormScreen,
  DocumentFormScreen,
  EventFormScreen,
  OutstandingFormScreen,
  StoryFormScreen,
} from '../screens/ContentManageScreens';
import { LoginScreen } from '../screens/LoginScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { RegisterScreen } from '../screens/RegisterScreen';
import { PostDetailScreen } from '../screens/PostDetailScreen';
import { CreatePostScreen } from '../screens/CreatePostScreen';
import { EventDetailScreen } from '../screens/EventDetailScreen';
import { NotificationsScreen } from '../screens/NotificationsScreen';
import { PersonDetailScreen } from '../screens/PersonDetailScreen';
import { EditProfileScreen } from '../screens/EditProfileScreen';
import { PersonPickerScreen } from '../screens/PersonPickerScreen';
import { PhotoViewerScreen, StoryDetailScreen } from '../screens/HeritageDetailScreens';
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
          <Stack.Screen
            name="ForgotPassword"
            component={ForgotPasswordScreen}
            options={{ title: 'Quên mật khẩu' }}
          />
        </>
      )}
      {branch === 'app' && (
        <>
          <Stack.Screen name="App" component={AppDrawer} options={{ headerShown: false }} />
          <Stack.Screen
            name="PostDetail"
            component={PostDetailScreen}
            options={{ title: 'Chi tiết bài viết' }}
          />
          <Stack.Screen
            name="CreatePost"
            component={CreatePostScreen}
            options={{ title: 'Tạo bài đăng' }}
          />
          <Stack.Screen
            name="EventDetail"
            component={EventDetailScreen}
            options={{ title: 'Chi tiết sự kiện' }}
          />
          <Stack.Screen
            name="PersonDetail"
            component={PersonDetailScreen}
            options={{ title: 'Thông tin thành viên' }}
          />
          <Stack.Screen
            name="EditProfile"
            component={EditProfileScreen}
            options={{ title: 'Chỉnh sửa hồ sơ' }}
          />
          <Stack.Screen
            name="PersonPicker"
            component={PersonPickerScreen}
            options={({ route }) => ({ title: route.params.title })}
          />
          <Stack.Screen
            name="StoryDetail"
            component={StoryDetailScreen}
            options={{ title: 'Câu chuyện gia đình' }}
          />
          <Stack.Screen
            name="PhotoViewer"
            component={PhotoViewerScreen}
            options={{ title: 'Xem ảnh' }}
          />
          <Stack.Screen
            name="Notifications"
            component={NotificationsScreen}
            options={{ title: 'Thông báo' }}
          />
          {/* Quản lý gia phả, nhóm Quản lý, Quản trị và form nội dung: hộp thoại của web là màn của stack. */}
          <Stack.Screen
            name="RelativeForm"
            component={RelativeFormScreen}
            options={{ title: 'Thêm người thân' }}
          />
          <Stack.Screen
            name="PersonForm"
            component={PersonFormScreen}
            options={{ title: 'Thành viên' }}
          />
          <Stack.Screen
            name="DeathForm"
            component={DeathFormScreen}
            options={{ title: 'Báo mất' }}
          />
          <Stack.Screen
            name="DeathReportForm"
            component={DeathReportFormScreen}
            options={{ title: 'Báo mất' }}
          />
          <Stack.Screen
            name="Divorce"
            component={DivorceScreen}
            options={{ title: 'Ghi ly hôn' }}
          />
          <Stack.Screen
            name="ReplaceAccount"
            component={ReplaceAccountScreen}
            options={{ title: 'Thay tài khoản' }}
          />
          <Stack.Screen
            name="ResetCode"
            component={ResetCodeScreen}
            options={{ title: 'Đặt lại mật khẩu' }}
          />
          <Stack.Screen
            name="ApproveRequest"
            component={ApproveRequestScreen}
            options={{ title: 'Duyệt yêu cầu' }}
          />
          <Stack.Screen
            name="RejectRequest"
            component={RejectRequestScreen}
            options={{ title: 'Từ chối yêu cầu' }}
          />
          <Stack.Screen name="BranchForm" component={BranchFormScreen} options={{ title: 'Chi' }} />
          <Stack.Screen
            name="PickBranchHead"
            component={PickBranchHeadScreen}
            options={{ title: 'Trưởng chi' }}
          />
          <Stack.Screen
            name="AccountRole"
            component={AccountRoleScreen}
            options={{ title: 'Phân công' }}
          />
          <Stack.Screen
            name="Invite"
            component={InviteScreen}
            options={{ title: 'Mời tham gia' }}
          />
          <Stack.Screen
            name="AdminLock"
            component={AdminLockScreen}
            options={{ title: 'Khóa tài khoản' }}
          />
          <Stack.Screen
            name="AdminAssign"
            component={AdminAssignScreen}
            options={{ title: 'Gán dòng họ' }}
          />
          <Stack.Screen
            name="AnnouncementForm"
            component={AnnouncementFormScreen}
            options={{ title: 'Thông báo' }}
          />
          <Stack.Screen
            name="EventForm"
            component={EventFormScreen}
            options={{ title: 'Sự kiện' }}
          />
          <Stack.Screen
            name="DocumentForm"
            component={DocumentFormScreen}
            options={{ title: 'Tài liệu' }}
          />
          <Stack.Screen
            name="StoryForm"
            component={StoryFormScreen}
            options={{ title: 'Câu chuyện' }}
          />
          <Stack.Screen
            name="OutstandingForm"
            component={OutstandingFormScreen}
            options={{ title: 'Người tiêu biểu' }}
          />
        </>
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
