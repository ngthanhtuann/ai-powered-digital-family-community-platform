// Menu drawer trong ứng dụng (mobile.md mục 3.1; không dùng tab bar). Nhóm Quản lý, Quản trị chỉ có trên web.
// Các module khác (Bảng tin, Sự kiện, Gia phả, AI...) thêm màn vào đây và vào AppDrawerParamList.
import {
  createDrawerNavigator,
  DrawerContentScrollView,
  DrawerItem,
  DrawerItemList,
  type DrawerContentComponentProps,
} from '@react-navigation/drawer';
import type { DrawerNavigationProp } from '@react-navigation/drawer';
import { useNavigation } from '@react-navigation/native';
import type { ComponentType } from 'react';
import { Pressable, Text, View } from 'react-native';
import { ChangePasswordScreen } from '../screens/ChangePasswordScreen';
import { HomeScreen } from '../screens/HomeScreen';
import { ProfileScreen } from '../screens/ProfileScreen';
import { AiChatScreen } from '../screens/AiChatScreen';
import { AiSearchScreen } from '../screens/AiSearchScreen';
import { AiSourceDetailScreen } from '../screens/AiSourceDetailScreen';
import { useSession } from '../session/SessionContext';
import { colors, text } from '../theme';
import type { AppDrawerParamList } from './types';

const Drawer = createDrawerNavigator<AppDrawerParamList>();

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
          style={{ position: 'absolute', right: 16, bottom: 20, minHeight: 48, borderRadius: 24,
            paddingHorizontal: 18, justifyContent: 'center', backgroundColor: colors.primary,
            borderWidth: 1, borderColor: colors.secondary, elevation: 5 }}
        >
          <Text style={[text.label, { color: colors.creamOnDark }]}>Trợ lý AI</Text>
        </Pressable>
      </View>
    );
  };
}

const HomeWithFab = withAssistantFab(HomeScreen);
const AiChatWithFab = withAssistantFab(AiChatScreen);
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
      screenOptions={{
        headerStyle: { backgroundColor: colors.surface },
        headerTintColor: colors.textPrimary,
        sceneStyle: { backgroundColor: colors.background },
        drawerActiveBackgroundColor: colors.secondary,
        drawerActiveTintColor: colors.textPrimary,
        drawerInactiveTintColor: colors.creamOnDark,
      }}
    >
      <Drawer.Screen name="Home" component={HomeWithFab} options={{ title: 'Tổng quan' }} />
      <Drawer.Screen name="AiChat" component={AiChatWithFab} options={{ title: 'Trợ lý AI' }} />
      <Drawer.Screen name="AiSearch" component={AiSearchWithFab} options={{ title: 'Tìm kiếm' }} />
      <Drawer.Screen name="AiSourceDetail" component={AiSourceDetailWithFab} options={{ title: 'Chi tiết', drawerItemStyle: { display: 'none' } }} />
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
