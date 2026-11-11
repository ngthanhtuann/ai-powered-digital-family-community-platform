import { useMutation, useQuery } from '@tanstack/react-query';
import { ScrollView, Text, View } from 'react-native';
import type { DrawerScreenProps } from '@react-navigation/drawer';
import { Button, Card, Loading, Notice } from '../components/ui';
import { ApiError, api } from '../services/api';
import { aiApi, type AiSourceType } from '../services/ai';
import type { AppDrawerParamList } from '../navigation/types';
import { colors, spacing, text } from '../theme';

type Props = DrawerScreenProps<AppDrawerParamList, 'AiSourceDetail'>;
type Detail = Record<string, unknown>;

const TYPE_NAME: Record<AiSourceType, string> = {
  PERSON: 'Thành viên',
  POST: 'Bài đăng',
  STORY: 'Câu chuyện',
  DOCUMENT: 'Tài liệu',
  EVENT: 'Sự kiện',
  PHOTO: 'Ảnh di sản',
};

/** API chi tiết của từng loại nguồn; ảnh di sản chưa có màn xem trên mobile. */
function endpoint(type: AiSourceType, id: string) {
  switch (type) {
    case 'PERSON':
      return `/persons/${id}`;
    case 'POST':
      return `/posts/${id}`;
    case 'STORY':
      return `/heritage/stories/${id}`;
    case 'DOCUMENT':
      return `/heritage/documents/${id}`;
    case 'EVENT':
      return `/events/${id}`;
    case 'PHOTO':
      return null;
  }
}

function textField(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null;
}

function errorText(cause: unknown, fallback: string) {
  return cause instanceof ApiError ? cause.message : fallback;
}

/** SCRUM-91: mở nguồn trích dẫn hoặc kết quả tìm kiếm; bài đăng và câu chuyện có nút "Tóm tắt bằng AI". */
export function AiSourceDetailScreen({ route }: Props) {
  const { sourceType, sourceId, title: fallbackTitle, snippet } = route.params;
  const path = endpoint(sourceType, sourceId);
  const canSummarize = sourceType === 'POST' || sourceType === 'STORY';
  // Khóa theo nguồn: mở nguồn khác thì không hiện nhầm nội dung hay tóm tắt của nguồn trước.
  const detail = useQuery({
    queryKey: ['ai-source', sourceType, sourceId],
    queryFn: () => api.get<Detail>(path as string),
    enabled: path !== null,
  });
  const summary = useMutation({
    mutationFn: (source: { type: 'POST' | 'STORY'; id: string }) =>
      aiApi.summarize(source.type, source.id),
  });
  // Màn hình được dùng lại khi mở nguồn khác: chỉ hiện kết quả, lỗi của đúng nguồn đang xem.
  const summaryIsCurrent = summary.variables?.id === sourceId;
  const summarize = () => summary.mutate({ type: sourceType as 'POST' | 'STORY', id: sourceId });

  const data = detail.data;
  const title =
    textField(data?.title) ?? textField(data?.fullName) ?? fallbackTitle ?? TYPE_NAME[sourceType];
  const content =
    textField(data?.content) ?? textField(data?.description) ?? textField(data?.snippet) ?? snippet;

  return (
    <ScrollView contentContainerStyle={{ padding: spacing.page, paddingBottom: 96, gap: 16 }}>
      <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>{title}</Text>
      {path === null ? (
        <Notice
          variant="info"
          title="Chưa xem được trên ứng dụng"
          message="Ảnh di sản hiện chỉ xem được trên bản web."
        />
      ) : null}
      {detail.isLoading ? <Loading label="Đang tải chi tiết…" /> : null}
      {detail.isError ? (
        <>
          <Notice
            variant="error"
            title="Không mở được chi tiết"
            message={errorText(detail.error, 'Không tải được nội dung.')}
          />
          <Button title="Thử tải lại" variant="secondary" onPress={() => void detail.refetch()} />
        </>
      ) : null}
      {data ? (
        <Card title={TYPE_NAME[sourceType]}>
          {content ? (
            <Text style={[text.body, { color: colors.textPrimary }]}>{content}</Text>
          ) : (
            <Text style={[text.body, { color: colors.textSecondary }]}>
              Chưa có nội dung chi tiết.
            </Text>
          )}
          {textField(data.authorName) ? (
            <Text style={[text.caption, { color: colors.textSecondary }]}>
              Đăng bởi {String(data.authorName)}
            </Text>
          ) : null}
        </Card>
      ) : null}
      {canSummarize ? (
        <View style={{ gap: 10 }}>
          <Button
            title="Tóm tắt bằng AI"
            onPress={summarize}
            loading={summary.isPending && summaryIsCurrent}
            disabled={!data}
          />
          {summary.isError && summaryIsCurrent ? (
            <>
              <Notice
                variant="error"
                title="Tóm tắt chưa hoàn tất"
                message={errorText(summary.error, 'Chưa tóm tắt được. Vui lòng thử lại.')}
              />
              <Button title="Thử lại" variant="secondary" onPress={summarize} />
            </>
          ) : null}
          {summary.data && summaryIsCurrent ? (
            <Card title="Tóm tắt AI">
              <Text style={[text.body, { color: colors.textPrimary }]}>{summary.data.summary}</Text>
            </Card>
          ) : null}
        </View>
      ) : null}
    </ScrollView>
  );
}
