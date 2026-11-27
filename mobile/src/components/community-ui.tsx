import { useQuery } from '@tanstack/react-query';
import type * as ImagePicker from 'expo-image-picker';
import { useMemo, useState } from 'react';
import { ActivityIndicator, Image, Pressable, ScrollView, Text, View } from 'react-native';
import type { ApiMeta } from '../services/api-types';
import { API_BASE_URL } from '../services/api';
import { communityApi, type Media, type UploadAsset } from '../services/community';
import { useFamilyScope } from '../session/FamilyScope';
import { tokenStorage } from '../services/token-storage';
import { colors, text } from '../theme';
import { Button, Card, Notice } from './ui';

/** Danh sách chi của gia đình đang đăng nhập và tên chi theo id, dùng cho ô "Ai được xem" và nhãn phạm vi. */
export function useFamilyBranches() {
  const familyId = useFamilyScope().familyId;
  const query = useQuery({
    queryKey: ['branches', familyId],
    queryFn: () => communityApi.branches(familyId as string),
    enabled: Boolean(familyId),
  });
  const items = useMemo(() => query.data?.items ?? [], [query.data]);
  const names = useMemo(
    () => new Map(items.map((branch) => [branch.id ?? '', branch.name ?? ''])),
    [items],
  );
  return { query, items, names };
}

export function formatWhen(date?: string | null) {
  if (!date) return '';
  const parsed = new Date(date);
  return Number.isNaN(parsed.getTime()) ? date : parsed.toLocaleString('vi-VN');
}

export function nextPage<T>(last: { items: T[]; meta: ApiMeta }): number | undefined {
  return (last.meta.page + 1) * last.meta.size < last.meta.total ? last.meta.page + 1 : undefined;
}

export function ErrorPanel({ error, retry }: { error: unknown; retry: () => void }) {
  return (
    <View style={{ padding: 16, gap: 12 }}>
      <Notice
        variant="error"
        title="Không tải được dữ liệu"
        message={error instanceof Error ? error.message : 'Vui lòng thử lại.'}
      />
      <Button title="Thử lại" onPress={retry} variant="secondary" />
    </View>
  );
}

export function EmptyPanel({ message }: { message: string }) {
  return (
    <View style={{ padding: 16 }}>
      <Card>
        <Text style={[text.body, { color: colors.textSecondary, textAlign: 'center' }]}>
          {message}
        </Text>
      </Card>
    </View>
  );
}

export function ListLoadingMore({ loading }: { loading: boolean }) {
  return loading ? (
    <View style={{ padding: 16 }}>
      <ActivityIndicator color={colors.primary} />
    </View>
  ) : null;
}

/** Media chỉ đọc được sau khi gửi Bearer JWT; không dùng URL public. */
export function MediaImage({ media }: { media: Media }) {
  const [failed, setFailed] = useState(false);
  const access = tokenStorage.getAccess();
  if (failed || !access) {
    return (
      <Pressable
        onPress={() => setFailed(false)}
        accessibilityRole="button"
        accessibilityLabel="Thử tải lại ảnh"
        style={{
          height: 150,
          width: 180,
          backgroundColor: colors.surfaceMuted,
          justifyContent: 'center',
          alignItems: 'center',
          borderRadius: 6,
        }}
      >
        <Text style={[text.caption, { color: colors.textSecondary }]}>
          Không xem được ảnh · Chạm để thử lại
        </Text>
      </Pressable>
    );
  }
  return (
    <Image
      accessibilityLabel={media.caption || media.fileName}
      source={{
        uri: `${API_BASE_URL}/api/v1/media/${encodeURIComponent(media.id)}/content`,
        headers: { Authorization: `Bearer ${access}` },
      }}
      resizeMode="cover"
      onError={() => setFailed(true)}
      style={{ height: 150, width: 180, borderRadius: 6, backgroundColor: colors.surfaceMuted }}
    />
  );
}

export function MediaGallery({ media }: { media: Media[] }) {
  if (!media.length) return null;
  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      contentContainerStyle={{ gap: 8 }}
    >
      {media.map((item) => (
        <MediaImage key={item.id} media={item} />
      ))}
    </ScrollView>
  );
}

/** Ảnh chọn từ máy thành tệp tải lên: chỉ JPG, PNG, WebP, tối đa 5 MB (conventions.md mục 7). Dùng cho bài đăng và ảnh đại diện. */
const IMAGE_MAX_BYTES = 5 * 1024 * 1024;
const IMAGE_MIMES = ['image/jpeg', 'image/png', 'image/webp'];

export function toUploadAsset(asset: ImagePicker.ImagePickerAsset): UploadAsset {
  const extension = (asset.fileName ?? asset.uri).split('?')[0].split('.').pop()?.toLowerCase();
  const type =
    asset.mimeType ??
    (extension === 'jpg' || extension === 'jpeg'
      ? 'image/jpeg'
      : extension === 'png'
        ? 'image/png'
        : extension === 'webp'
          ? 'image/webp'
          : '');
  if (!IMAGE_MIMES.includes(type)) {
    throw new Error(
      'Chỉ hỗ trợ ảnh JPG, PNG hoặc WebP. Ảnh HEIC cần chuyển sang JPG trước khi chọn.',
    );
  }
  if (asset.fileSize && asset.fileSize > IMAGE_MAX_BYTES) {
    throw new Error('Mỗi ảnh được chọn phải nhỏ hơn hoặc bằng 5 MB.');
  }
  const suffix = type === 'image/jpeg' ? 'jpg' : type === 'image/png' ? 'png' : 'webp';
  const name =
    asset.fileName && asset.fileName.toLowerCase().endsWith(`.${suffix}`)
      ? asset.fileName
      : `anh-${Date.now()}.${suffix}`;
  return { uri: asset.uri, name, mimeType: type };
}
