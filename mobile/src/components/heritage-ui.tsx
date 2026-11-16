import { useState } from 'react';
import { Image, Pressable, Text, type ImageStyle, type StyleProp } from 'react-native';
import { API_BASE_URL } from '../services/api';
import { tokenStorage } from '../services/token-storage';
import { colors, text } from '../theme';

/**
 * Ảnh media có kiểm tra quyền: gửi Bearer JWT (không có URL public). Backend không có thumbnail nên FlatList chỉ
 * tải ảnh đang hiện (tải lười); lỗi thì hiện ô chạm để thử lại.
 */
export function AuthImage({
  mediaId,
  label,
  style,
  resizeMode = 'cover',
  onFail,
}: {
  mediaId: string;
  label: string;
  style: StyleProp<ImageStyle>;
  resizeMode?: 'cover' | 'contain';
  onFail?: () => void;
}) {
  const [failed, setFailed] = useState(false);
  const access = tokenStorage.getAccess();
  if (failed || !access) {
    return (
      <Pressable
        onPress={() => setFailed(false)}
        accessibilityRole="button"
        accessibilityLabel={`Thử tải lại ${label}`}
        style={[
          style as object,
          { backgroundColor: colors.surfaceMuted, justifyContent: 'center', alignItems: 'center' },
        ]}
      >
        <Text style={[text.caption, { color: colors.textSecondary, textAlign: 'center' }]}>
          Không xem được · Chạm để thử lại
        </Text>
      </Pressable>
    );
  }
  return (
    <Image
      accessibilityLabel={label}
      source={{
        uri: `${API_BASE_URL}/api/v1/media/${encodeURIComponent(mediaId)}/content`,
        headers: { Authorization: `Bearer ${access}` },
      }}
      resizeMode={resizeMode}
      onError={() => {
        setFailed(true);
        onFail?.();
      }}
      style={[{ backgroundColor: colors.surfaceMuted }, style]}
    />
  );
}

export function formatDate(value?: string | null) {
  if (!value) return '';
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? value : parsed.toLocaleDateString('vi-VN');
}
