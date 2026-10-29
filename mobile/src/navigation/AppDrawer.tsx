// Menu drawer trong ứng dụng (mobile.md mục 3.1; không dùng tab bar). Nhóm Quản lý, Quản trị chỉ có trên web.
// Các module khác (Bảng tin, Sự kiện, Gia phả, AI...) thêm màn vào đây và vào AppDrawerParamList.
import {
  createDrawerNavigator,
  DrawerContentScrollView,
  DrawerItem,
  DrawerItemList,
  type DrawerContentComponentProps,
} from '@react-navigation/drawer';
import { Text, View } from 'react-native';
import { ChangePasswordScreen } from '../screens/ChangePasswordScreen';
import { HomeScreen } from '../screens/HomeScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { useSession } from '../session/SessionContext';
import { colors, text } from '../theme';
import type { AppDrawerParamList } from './types';

const Drawer = createDrawerNavigator<AppDrawerParamList>();

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
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.textPrimary,
        sceneStyle: { backgroundColor: colors.background },
        drawerActiveBackgroundColor: colors.secondary,
        drawerActiveTintColor: colors.textPrimary,
        drawerInactiveTintColor: colors.creamOnDark,
      }}
    >
      <Drawer.Screen name="Home" component={HomeScreen} options={{ title: 'Tổng quan' }} />
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
