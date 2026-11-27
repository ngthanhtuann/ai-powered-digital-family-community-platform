// Tóm tắt bằng AI (UC-AI-04) và "Có thể bạn quan tâm" (UC-AI-05), như AiSummaryPanel, RecommendationsPanel của web.
// Câu do AI tạo luôn gắn nhãn "Do AI tạo" (BR-AI-16).
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { Pressable, Text, View } from 'react-native';
import type { RootStackParamList } from '../navigation/types';
import { aiApi, type AiRecommendationContext, type AiSourceType } from '../services/ai';
import { colors, radius, text } from '../theme';
import { ErrorText } from './form-ui';
import { Button } from './ui';

export function AiSummaryPanel({
  sourceType,
  sourceId,
}: {
  sourceType: 'POST' | 'STORY' | 'DOCUMENT';
  sourceId: string;
}) {
  const summary = useMutation({ mutationFn: () => aiApi.summarize(sourceType, sourceId) });
  return (
    <View style={{ gap: 8 }}>
      <Button
        title={summary.isPending ? 'Đang tóm tắt...' : 'Tóm tắt bằng AI'}
        variant="secondary"
        loading={summary.isPending}
        onPress={() => summary.mutate()}
      />
      <ErrorText error={summary.error} />
      {summary.data ? (
        <View
          style={{
            backgroundColor: colors.surfaceMuted,
            borderRadius: radius.card,
            padding: 12,
            gap: 4,
          }}
        >
          <Text style={[text.caption, { color: colors.textSecondary, fontWeight: '600' }]}>
            Do AI tạo
          </Text>
          <Text style={[text.body, { color: colors.textPrimary }]}>{summary.data.summary}</Text>
        </View>
      ) : null}
    </View>
  );
}

/** Chỉ tải khi người dùng bấm (như deferred trong bảng tin web) để không gọi API cho mọi bài. */
export function RecommendationsPanel({
  contextType,
  contextId,
}: {
  contextType: AiRecommendationContext;
  contextId: string;
}) {
  const navigation = useNavigation<NativeStackNavigationProp<RootStackParamList>>();
  const [open, setOpen] = useState(false);
  const query = useQuery({
    queryKey: ['ai-recommendations', contextType, contextId],
    queryFn: () => aiApi.recommendations(contextType, contextId),
    enabled: open && Boolean(contextId),
    retry: false,
  });
  if (!open)
    return (
      <Button title="Xem nội dung liên quan" variant="secondary" onPress={() => setOpen(true)} />
    );
  if (query.isLoading) {
    return (
      <Text style={[text.body, { color: colors.textSecondary }]}>Đang tìm nội dung liên quan…</Text>
    );
  }
  if (query.isError) return <ErrorText error={query.error} />;
  const items = query.data?.recommendations ?? [];
  if (!items.length) {
    return (
      <Text style={[text.body, { color: colors.textSecondary }]}>Chưa có nội dung liên quan.</Text>
    );
  }
  const openItem = (sourceType: string, sourceId: string, title?: string, snippet?: string) => {
    if (sourceType === 'PERSON') navigation.push('PersonDetail', { personId: sourceId });
    else
      navigation.navigate('App', {
        screen: 'AiSourceDetail',
        params: { sourceType: sourceType as AiSourceType, sourceId, title, snippet },
      });
  };
  return (
    <View style={{ gap: 8 }} accessibilityLabel="Có thể bạn quan tâm">
      <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>Có thể bạn quan tâm</Text>
      {items.map((item) => (
        <Pressable
          key={`${item.sourceType}-${item.sourceId}`}
          accessibilityRole="button"
          onPress={() => openItem(item.sourceType, item.sourceId, item.title, item.snippet)}
          style={{
            borderWidth: 1,
            borderColor: colors.border,
            borderRadius: radius.card,
            padding: 12,
            gap: 2,
          }}
        >
          <Text style={[text.caption, { color: colors.textSecondary }]}>
            {item.sourceType === 'PERSON' ? 'Thành viên' : 'Tài nguyên gia đình'}
          </Text>
          <Text style={[text.label, { color: colors.textPrimary }]}>
            {item.title || 'Nội dung gia đình'}
          </Text>
          {item.reason ? (
            <Text style={[text.caption, { color: colors.textSecondary }]}>{item.reason}</Text>
          ) : null}
          {item.snippet ? (
            <Text style={[text.body, { color: colors.textSecondary }]} numberOfLines={2}>
              {item.snippet}
            </Text>
          ) : null}
        </Pressable>
      ))}
    </View>
  );
}
