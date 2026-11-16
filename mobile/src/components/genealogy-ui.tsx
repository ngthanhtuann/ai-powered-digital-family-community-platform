// Component dùng chung cho màn Cây gia phả, Tra cứu xưng hô, Thông tin thành viên (UC-GEN-06, 07, 08; SCRUM-90).
import { useState } from 'react';
import { Image, Pressable, Text, View } from 'react-native';
import { API_BASE_URL } from '../services/api';
import { tokenStorage } from '../services/token-storage';
import { colors, radius, size, text } from '../theme';

type Gender = 'MALE' | 'FEMALE' | 'OTHER' | null | undefined;

/** Màu giới tính theo Figma "UC-GEN-06 Cây gia phả / 360"; luôn có chú giải bằng chữ đi kèm. */
export function genderColor(gender: Gender): string {
  if (gender === 'MALE') return colors.secondary;
  if (gender === 'FEMALE') return colors.accentPink;
  return colors.borderStrong;
}

export const GENDER_LABEL: Record<'MALE' | 'FEMALE' | 'OTHER', string> = {
  MALE: 'Nam',
  FEMALE: 'Nữ',
  OTHER: 'Khác',
};

/** Ảnh qua API có kiểm quyền (gửi Bearer JWT); không có ảnh hoặc lỗi thì hiện chữ cái đầu của tên. */
export function PersonAvatar({
  name,
  gender,
  mediaId,
  size: box = 38,
}: {
  name?: string | null;
  gender?: Gender;
  mediaId?: string | null;
  size?: number;
}) {
  const [failed, setFailed] = useState(false);
  const access = tokenStorage.getAccess();
  const initial = (name?.trim().split(/\s+/).pop() ?? '?').charAt(0).toUpperCase();
  const shape = { width: box, height: box, borderRadius: box / 2 };
  if (mediaId && access && !failed) {
    return (
      <Image
        accessibilityIgnoresInvertColors
        source={{
          uri: `${API_BASE_URL}/api/v1/media/${encodeURIComponent(mediaId)}/content`,
          headers: { Authorization: `Bearer ${access}` },
        }}
        onError={() => setFailed(true)}
        style={[shape, { backgroundColor: colors.surfaceMuted }]}
      />
    );
  }
  return (
    <View
      importantForAccessibility="no"
      style={[
        shape,
        { backgroundColor: genderColor(gender), alignItems: 'center', justifyContent: 'center' },
      ]}
    >
      <Text style={{ color: colors.surface, fontWeight: '700', fontSize: box * 0.42 }}>
        {initial}
      </Text>
    </View>
  );
}

/** Ô chọn giống select (cao 42): bấm để mở màn Chọn thành viên. */
export function PersonSelect({
  label,
  value,
  onPress,
}: {
  label: string;
  value?: string | null;
  onPress: () => void;
}) {
  return (
    <View style={{ gap: 6 }}>
      <Text style={[text.label, { color: colors.textPrimary }]}>{label}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}: ${value ?? 'chưa chọn'}`}
        onPress={onPress}
        style={{
          minHeight: size.input,
          borderWidth: 1,
          borderColor: colors.border,
          borderRadius: radius.control,
          backgroundColor: colors.surface,
          paddingHorizontal: 12,
          flexDirection: 'row',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}
      >
        <Text
          style={[text.body, { color: value ? colors.textPrimary : colors.textSecondary, flex: 1 }]}
        >
          {value ?? 'Chọn thành viên'}
        </Text>
        <Text style={{ color: colors.textSecondary }}>▾</Text>
      </Pressable>
    </View>
  );
}
