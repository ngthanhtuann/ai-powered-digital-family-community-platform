// Màn chi tiết của Di sản (SCRUM-92): đọc câu chuyện, xem ảnh hoặc tài liệu phóng to. Màn của stack để có nút quay lại.
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { ScrollView, Text, useWindowDimensions, View } from 'react-native';
import { ErrorPanel } from '../components/community-ui';
import { AuthImage, formatDate } from '../components/heritage-ui';
import { Card, Loading, Notice, Screen } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { heritageApi } from '../services/heritage';
import { colors, spacing, text } from '../theme';

export function StoryDetailScreen({
  route,
}: NativeStackScreenProps<RootStackParamList, 'StoryDetail'>) {
  const story = useQuery({
    queryKey: ['heritage', 'story', route.params.storyId],
    queryFn: () => heritageApi.story(route.params.storyId),
  });
  if (story.isPending) return <Loading />;
  if (story.isError) {
    return (
      <Screen>
        <ErrorPanel error={story.error} retry={() => story.refetch()} />
      </Screen>
    );
  }
  const s = story.data;
  return (
    <Screen>
      <Card title={s.title} description={`${s.authorName} · ${formatDate(s.createdAt)}`}>
        <Text style={[text.body, { color: colors.textPrimary }]}>{s.content}</Text>
      </Card>
    </Screen>
  );
}

/**
 * Xem ảnh phóng to (thư viện ảnh) hoặc tài liệu trong kho lưu trữ. Tài liệu PDF không hiển thị được bằng Image và
 * mobile chưa có thư viện tải tệp (cần duyệt thêm package), nên báo người dùng mở trên web.
 */
export function PhotoViewerScreen({
  route,
}: NativeStackScreenProps<RootStackParamList, 'PhotoViewer'>) {
  const { mediaId, title, document } = route.params;
  const { width, height } = useWindowDimensions();
  const [failed, setFailed] = useState(false);
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{ padding: spacing.page, gap: 12 }}
      maximumZoomScale={4}
      minimumZoomScale={1}
    >
      <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>{title}</Text>
      {failed && document ? (
        <Notice
          variant="info"
          title="Không xem được tệp này trên điện thoại"
          message="Tài liệu có thể là PDF. Mở mục Kho lưu trữ số trên web để xem hoặc tải về."
        />
      ) : null}
      <View>
        <AuthImage
          mediaId={mediaId}
          label={title}
          resizeMode="contain"
          onFail={() => setFailed(true)}
          style={{ width: width - spacing.page * 2, height: height * 0.7, borderRadius: 6 }}
        />
      </View>
    </ScrollView>
  );
}
