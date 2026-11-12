// Menu drawer trong ứng dụng (mobile.md mục 3.1; không dùng tab bar). Nhóm Quản lý, Quản trị chỉ có trên web.
// Các module khác (Bảng tin, Sự kiện, Gia phả, AI...) thêm màn vào đây và vào AppDrawerParamList.
import {
  createDrawerNavigator,
  DrawerContentScrollView,
  DrawerItem,
  DrawerItemList,
  type DrawerContentComponentProps,
  type DrawerNavigationProp,
} from '@react-navigation/drawer';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useNavigation } from '@react-navigation/native';
import type { ComponentType } from 'react';
import { Pressable, Text, View } from 'react-native';
import { ChangePasswordScreen } from '../screens/ChangePasswordScreen';
import { EventsScreen } from '../screens/EventsScreen';
import { FeedScreen } from '../screens/FeedScreen';
import { HomeScreen } from '../screens/HomeScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { notificationsApi } from '../services/notifications';
import { AiChatScreen } from '../screens/AiChatScreen';
import { AiSearchScreen } from '../screens/AiSearchScreen';
import { AiSourceDetailScreen } from '../screens/AiSourceDetailScreen';
import { useSession } from '../session/SessionContext';
import { colors, text } from '../theme';
import type { AppDrawerParamList, RootStackParamList } from './types';

const Drawer = createDrawerNavigator<AppDrawerParamList>();

function NotificationBell({ onPress }: { onPress: () => void }) {
  const { data: unread = 0 } = useQuery({
    queryKey: ['notifications', 'unread'],
    queryFn: notificationsApi.unreadCount,
    refetchInterval: 30000,
  });
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={`Thông báo, ${unread} chưa đọc`}
      style={{
        paddingHorizontal: 16,
        paddingVertical: 8,
        flexDirection: 'row',
        alignItems: 'center',
      }}
    >
      <Text style={{ fontSize: 22 }}>🔔</Text>
      {unread > 0 ? (
        <View
          style={{
            backgroundColor: colors.error,
            borderRadius: 12,
            minWidth: 18,
            height: 18,
            alignItems: 'center',
            justifyContent: 'center',
            marginLeft: -6,
            marginTop: -15,
            paddingHorizontal: 3,
          }}
        >
          <Text style={{ color: colors.surface, fontSize: 10, fontWeight: '700' }}>
            {unread > 99 ? '99+' : unread}
          </Text>
        </View>
      ) : null}
    </Pressable>
  );
}

function withAssistantFab<P extends object>(Screen: ComponentType<P>) {
  return function ScreenWithAssistantFab(props: P) {
    const navigation = useNavigation<DrawerNavigationProp<AppDrawerParamList>>();
    return (
      <View style={{ flex: 1 }}>
        <Screen {...props} />
        <Pressable
          accessibilityRole="button"
          accessibilityLabel="Trợ lý AI"
          onPress={() => navigation.navigate('AiChat')}
          style={{
            position: 'absolute',
            right: 16,
            bottom: 20,
            minHeight: 48,
            borderRadius: 24,
            paddingHorizontal: 18,
            justifyContent: 'center',
            backgroundColor: colors.primary,
            borderWidth: 1,
            borderColor: colors.secondary,
            elevation: 5,
          }}
        >
          <Text style={[text.label, { color: colors.creamOnDark }]}>Trợ lý AI</Text>
        </Pressable>
      </View>
    );
  };
}

const HomeWithFab = withAssistantFab(HomeScreen);
const AiSearchWithFab = withAssistantFab(AiSearchScreen);
const AiSourceDetailWithFab = withAssistantFab(AiSourceDetailScreen);
const ProfileWithFab = withAssistantFab(ProfileScreen);
const ChangePasswordWithFab = withAssistantFab(ChangePasswordScreen);

function DrawerContent(props: DrawerContentComponentProps) {
  const { session, signOut } = useSession();
  const familyName = session.status === 'signedIn' ? session.membership.familyName : null;
  return (
    <DrawerContentScrollView {...props} style={{ backgroundColor: colors.primary }}>
      <View style={{ paddingHorizontal: 16, paddingVertical: 12, gap: 2 }}>
        <Text style={[text.sectionTitle, { color: colors.creamOnDark }]}>FamilyConnect</Text>
        {familyName ? (
          <Text style={[text.caption, { color: colors.creamOnDark }]}>{familyName}</Text>
        ) : null}
      </View>
      <DrawerItemList {...props} />
      <DrawerItem
        label="Đăng xuất"
        labelStyle={{ color: colors.creamOnDark }}
        onPress={() => signOut()}
      />
    </DrawerContentScrollView>
  );
}

export function AppDrawer() {
  return (
    <Drawer.Navigator
      drawerContent={(props) => <DrawerContent {...props} />}
      screenOptions={({ navigation }) => ({
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.textPrimary,
        headerRight: () => (
          <NotificationBell
            onPress={() =>
              navigation
                .getParent<NativeStackNavigationProp<RootStackParamList>>()
                ?.navigate('Notifications')
            }
          />
        ),
        sceneStyle: { backgroundColor: colors.background },
        drawerActiveBackgroundColor: colors.secondary,
        drawerActiveTintColor: colors.textPrimary,
        drawerInactiveTintColor: colors.creamOnDark,
      })}
    >
      <Drawer.Screen name="Home" component={HomeWithFab} options={{ title: 'Tổng quan' }} />
      {/* Bảng tin, Sự kiện là danh sách cuộn: không gắn nút nổi Trợ lý AI để khỏi che bài cuối; mở AI từ menu. */}
      <Drawer.Screen name="Feed" component={FeedScreen} options={{ title: 'Bảng tin' }} />
      <Drawer.Screen name="Events" component={EventsScreen} options={{ title: 'Sự kiện' }} />
      <Drawer.Screen name="AiChat" component={AiChatScreen} options={{ title: 'Trợ lý AI' }} />
      <Drawer.Screen name="AiSearch" component={AiSearchWithFab} options={{ title: 'Tìm kiếm' }} />
      <Drawer.Screen
        name="AiSourceDetail"
        component={AiSourceDetailWithFab}
        options={{ title: 'Chi tiết', drawerItemStyle: { display: 'none' } }}
      />
      <Drawer.Screen
        name="Profile"
        component={ProfileWithFab}
        options={{ title: 'Hồ sơ cá nhân' }}
      />
      <Drawer.Screen
        name="ChangePassword"
        component={ChangePasswordWithFab}
        options={{ title: 'Đổi mật khẩu' }}
      />
    </Drawer.Navigator>
  );
}
