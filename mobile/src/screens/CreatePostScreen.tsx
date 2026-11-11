import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useQueryClient } from '@tanstack/react-query';
import * as ImagePicker from 'expo-image-picker';
import { useState } from 'react';
import { Image, Pressable, Text, TextInput, View } from 'react-native';
import { useFamilyBranches } from '../components/community-ui';
import { Button, Card, Notice, Screen } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import {
  communityApi,
  postableBranches,
  scopeLabel,
  type UploadAsset,
} from '../services/community';
import { useSession } from '../session/SessionContext';
import { colors, radius, text } from '../theme';

const IMAGE_MAX_BYTES = 5 * 1024 * 1024;
const IMAGE_MIMES = ['image/jpeg', 'image/png', 'image/webp'];

function toUploadAsset(asset: ImagePicker.ImagePickerAsset): UploadAsset {
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

export function CreatePostScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const queryClient = useQueryClient();
  const { session } = useSession();
  const [content, setContent] = useState('');
  const [assets, setAssets] = useState<UploadAsset[]>([]);
  const [postId, setPostId] = useState<string | null>(null);
  const [nextImage, setNextImage] = useState(0);
  const [progress, setProgress] = useState<number | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // "Ai được xem" giống web (SCRUM-101): mặc định chi của mình; chưa gán chi hoặc trưởng họ thì cả dòng họ.
  const branches = useFamilyBranches();
  const membership = session.status === 'signedIn' ? session.membership : null;
  const systemAdmin = session.status === 'signedIn' && session.user.roles.includes('SYSTEM_ADMIN');
  const branchAdmin = membership?.role === 'BRANCH_ADMIN';
  const myBranch = membership?.branchId ?? null;
  const scopeOptions = postableBranches(
    branches.items,
    systemAdmin || (branchAdmin && !myBranch),
    myBranch,
    branchAdmin,
  );
  const [scopeChoice, setScopeChoice] = useState<string | null | undefined>(undefined);
  const wanted = scopeChoice === undefined ? myBranch : scopeChoice;
  const scope = wanted && scopeOptions.some((b) => b.id === wanted) ? wanted : null;

  const addAssets = (selected: ImagePicker.ImagePickerAsset[]) => {
    try {
      setAssets((current) => [...current, ...selected.map(toUploadAsset)]);
      setError(null);
    } catch (caught) {
      setError((caught as Error).message);
    }
  };

  const pickFromLibrary = async () => {
    try {
      const result = await ImagePicker.launchImageLibraryAsync({
        mediaTypes: ['images'],
        allowsMultipleSelection: true,
        quality: 0.8,
      });
      if (!result.canceled) addAssets(result.assets);
    } catch (caught) {
      setError((caught as Error).message);
    }
  };

  const takePhoto = async () => {
    try {
      const permission = await ImagePicker.requestCameraPermissionsAsync();
      if (!permission.granted) {
        setError('Ứng dụng cần quyền camera để chụp ảnh. Hãy cấp quyền trong cài đặt.');
        return;
      }
      const result = await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 0.8 });
      if (!result.canceled) addAssets(result.assets);
    } catch (caught) {
      setError((caught as Error).message);
    }
  };

  const publish = async () => {
    if (!postId && (!content.trim() || content.trim().length > 5000)) {
      setError('Nội dung bài đăng phải có 1–5000 ký tự.');
      return;
    }
    setBusy(true);
    setError(null);
    let id = postId;
    try {
      if (!id) {
        const familyId =
          session.status === 'signedIn' ? (session.membership.familyId ?? undefined) : undefined;
        id = (await communityApi.create(content.trim(), scope, familyId)).id;
        setPostId(id);
      }
      // Nếu tải ảnh lỗi, chỉ tải lại ảnh còn thiếu, không tạo bài trùng.
      for (let i = nextImage; i < assets.length; i++) {
        setProgress(0);
        await communityApi.uploadImage(id, assets[i], setProgress);
        setNextImage(i + 1);
      }
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['posts'] }),
        queryClient.invalidateQueries({ queryKey: ['post', id] }),
      ]);
      navigation.replace('PostDetail', { postId: id });
    } catch (caught) {
      // Dùng id cục bộ: bài vừa tạo trong lần bấm này thì state postId chưa kịp cập nhật.
      setError(`${(caught as Error).message}${id ? ' — hãy thử tải tiếp ảnh.' : ''}`);
    } finally {
      setBusy(false);
      setProgress(null);
    }
  };

  return (
    <Screen>
      <Card title="Bài đăng mới">
        <TextInput
          accessibilityLabel="Nội dung bài đăng"
          value={content}
          onChangeText={setContent}
          editable={!busy && !postId}
          placeholder="Bạn muốn chia sẻ điều gì với gia đình?"
          placeholderTextColor={colors.textSecondary}
          multiline
          maxLength={5000}
          style={{
            minHeight: 140,
            padding: 12,
            textAlignVertical: 'top',
            color: colors.textPrimary,
            borderColor: colors.border,
            borderWidth: 1,
            borderRadius: radius.control,
          }}
        />
        <Text style={[text.caption, { color: colors.textSecondary }]}>
          {content.length}/5000 ký tự
        </Text>
        <Text style={[text.label, { color: colors.textPrimary }]}>Ai được xem</Text>
        <View accessibilityRole="radiogroup" style={{ gap: 6 }}>
          {[{ id: null as string | null, label: 'Cả dòng họ' }]
            .concat(
              scopeOptions.map((b) => ({
                id: b.id ?? null,
                label: `${b.name}${b.id === myBranch ? ' (chi của tôi)' : ''}`,
              })),
            )
            .map((option) => (
              <Pressable
                key={option.id ?? 'family'}
                accessibilityRole="radio"
                accessibilityState={{ checked: scope === option.id, disabled: busy || !!postId }}
                disabled={busy || !!postId || branches.query.isPending}
                onPress={() => setScopeChoice(option.id)}
                style={{
                  minHeight: 44,
                  justifyContent: 'center',
                  paddingHorizontal: 12,
                  borderRadius: radius.control,
                  borderWidth: 1,
                  borderColor: scope === option.id ? colors.primary : colors.border,
                  backgroundColor: scope === option.id ? colors.secondary : colors.surface,
                }}
              >
                <Text style={[text.body, { color: colors.textPrimary }]}>{option.label}</Text>
              </Pressable>
            ))}
        </View>
        <Text style={[text.caption, { color: colors.textSecondary }]}>
          {branches.query.isPending
            ? 'Đang tải danh sách chi...'
            : branches.query.isError
              ? 'Không tải được danh sách chi, bài sẽ hiển thị cho cả dòng họ.'
              : scope
                ? `Chỉ người trong ${scopeLabel(scope, branches.names)} và các chi con thấy, cùng trưởng chi, trưởng họ quản lý.`
                : 'Mọi thành viên trong dòng họ đều thấy.'}
        </Text>
        <Button
          title="Chọn ảnh từ thư viện"
          variant="secondary"
          disabled={busy || !!postId}
          onPress={pickFromLibrary}
        />
        <Button
          title="Chụp ảnh bằng camera"
          variant="secondary"
          disabled={busy || !!postId}
          onPress={takePhoto}
        />
        {assets.map((asset, index) => (
          <View
            key={`${asset.uri}-${index}`}
            style={{ flexDirection: 'row', alignItems: 'center', gap: 12 }}
          >
            <Image source={{ uri: asset.uri }} style={{ height: 68, width: 68, borderRadius: 6 }} />
            <Text
              style={[text.caption, { flex: 1, color: colors.textSecondary }]}
              numberOfLines={2}
            >
              {asset.name}
              {index < nextImage ? ' · Đã tải' : ''}
            </Text>
            {!busy && !postId ? (
              <Button
                title="Bỏ"
                variant="secondary"
                onPress={() => setAssets((current) => current.filter((_, i) => i !== index))}
              />
            ) : null}
          </View>
        ))}
        {progress !== null ? (
          <Text style={[text.label, { color: colors.primary }]}>
            Đang tải ảnh {nextImage + 1}/{assets.length}: {progress}%
          </Text>
        ) : null}
        {error ? <Notice variant="error" title="Không đăng được bài" message={error} /> : null}
        <Button
          title={postId ? 'Tiếp tục tải ảnh' : 'Đăng bài'}
          loading={busy}
          disabled={branches.query.isPending}
          onPress={publish}
        />
        <Text style={[text.caption, { color: colors.textSecondary }]}>
          Mỗi ảnh tối đa 5 MB, định dạng JPG/PNG/WebP. Bài được tạo trước, ảnh sẽ được gắn vào bài
          sau.
        </Text>
      </Card>
    </Screen>
  );
}
