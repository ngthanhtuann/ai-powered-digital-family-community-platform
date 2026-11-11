// Menu drawer trong ứng dụng (mobile.md mục 3.1; không dùng tab bar). Nhóm Quản lý, Quản trị chỉ có trên web.
// Các module khác (Bảng tin, Sự kiện, Gia phả, AI...) thêm màn vào đây và vào AppDrawerParamList.
import {
  createDrawerNavigator,
  DrawerContentScrollView,
  DrawerItem,
  DrawerItemList,
  type DrawerContentComponentProps,
} from '@react-navigation/drawer';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { Pressable, Text, View } from 'react-native';
import { ChangePasswordScreen } from '../screens/ChangePasswordScreen';
import { EventsScreen } from '../screens/EventsScreen';
import { FeedScreen } from '../screens/FeedScreen';
import { HomeScreen } from '../screens/HomeScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { notificationsApi } from '../services/notifications';
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
      <Drawer.Screen name="Home" component={HomeScreen} options={{ title: 'Tổng quan' }} />
      <Drawer.Screen name="Feed" component={FeedScreen} options={{ title: 'Bảng tin' }} />
      <Drawer.Screen name="Events" component={EventsScreen} options={{ title: 'Sự kiện' }} />
      <Drawer.Screen
        name="Profile"
        component={ProfileScreen}
        options={{ title: 'Hồ sơ cá nhân' }}
      />
      <Drawer.Screen
        name="ChangePassword"
        component={ChangePasswordScreen}
        options={{ title: 'Đổi mật khẩu' }}
      />
    </Drawer.Navigator>
  );
}
