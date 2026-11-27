// Màn chi tiết của Di sản (SCRUM-92): đọc câu chuyện, xem ảnh hoặc tài liệu phóng to. Màn của stack để có nút quay lại.
import type { NativeStackScreenProps } from '@react-navigation/native-stack';
import { useMutation, useQuery } from '@tanstack/react-query';
import { File, Paths } from 'expo-file-system';
import { useState } from 'react';
import { ScrollView, Text, useWindowDimensions, View } from 'react-native';
import { ErrorPanel } from '../components/community-ui';
import { AuthImage, formatDate } from '../components/heritage-ui';
import { Button, Card, Loading, Notice, Screen } from '../components/ui';
import type { RootStackParamList } from '../navigation/types';
import { API_BASE_URL } from '../services/api';
import { heritageApi } from '../services/heritage';
import { tokenStorage } from '../services/token-storage';
import { colors, spacing, text } from '../theme';
import { DocumentManagePanel, StoryManagePanel } from './ContentManageScreens';
import { AiSummaryPanel } from '../components/ai-ui';

export function StoryDetailScreen({
  navigation,
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
      <AiSummaryPanel sourceType="STORY" sourceId={s.id} />
      <StoryManagePanel
        storyId={s.id}
        authorId={s.authorId}
        stack={navigation}
        onDeleted={() => navigation.goBack()}
      />
    </Screen>
  );
}

/**
 * Tài liệu di sản chỉ là ảnh hoặc PDF (conventions.md mục tải tệp). Không hiện được bằng Image thì là PDF: tải qua API
 * media có kiểm quyền vào bộ nhớ đệm rồi mở trình xem của máy (SCRUM-109).
 */
async function openPdf(mediaId: string, title: string) {
  const access = tokenStorage.getAccess();
  if (!access) throw new Error('Phiên đăng nhập đã hết, vui lòng đăng nhập lại.');
  const file = await File.downloadFileAsync(
    `${API_BASE_URL}/api/v1/media/${encodeURIComponent(mediaId)}/content`,
    new File(Paths.cache, `${mediaId}.pdf`),
    { headers: { Authorization: `Bearer ${access}` }, idempotent: true },
  );
  await File.preview(file, { title, mimeType: 'application/pdf' });
}

/** Xem ảnh phóng to (thư viện ảnh) hoặc tài liệu trong kho lưu trữ; tài liệu PDF mở bằng trình xem của máy. */
export function PhotoViewerScreen({
  navigation,
  route,
}: NativeStackScreenProps<RootStackParamList, 'PhotoViewer'>) {
  const { mediaId, title, document, documentId } = route.params;
  const { width, height } = useWindowDimensions();
  const [failed, setFailed] = useState(false);
  const pdf = useMutation({ mutationFn: () => openPdf(mediaId, title) });
  return (
    <ScrollView
      style={{ flex: 1, backgroundColor: colors.background }}
      contentContainerStyle={{ padding: spacing.page, gap: 12 }}
      maximumZoomScale={4}
      minimumZoomScale={1}
    >
      <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>{title}</Text>
      {documentId ? (
        <>
          <AiSummaryPanel sourceType="DOCUMENT" sourceId={documentId} />
          <DocumentManagePanel
            documentId={documentId}
            stack={navigation}
            onDeleted={() => navigation.goBack()}
          />
        </>
      ) : null}
      {failed && document ? (
        <View style={{ gap: 8 }}>
          <Notice
            variant="info"
            title="Tài liệu PDF"
            message="Mở bằng trình xem PDF của điện thoại."
          />
          <Button title="Mở tài liệu" loading={pdf.isPending} onPress={() => pdf.mutate()} />
          {pdf.isError ? (
            <Notice
              variant="error"
              title="Không mở được tài liệu"
              message={pdf.error instanceof Error ? pdf.error.message : 'Vui lòng thử lại.'}
            />
          ) : null}
        </View>
      ) : null}
      <View style={failed && document ? { display: 'none' } : undefined}>
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
