import { useEffect, useState } from 'react';
import { ScrollView, Text, View } from 'react-native';
import type { DrawerScreenProps } from '@react-navigation/drawer';
import { Button, Card, Loading, Notice } from '../components/ui';
import { ApiError, api } from '../services/api';
import { aiApi } from '../services/ai';
import type { AppDrawerParamList } from '../navigation/types';
import { colors, spacing, text } from '../theme';

type Props = DrawerScreenProps<AppDrawerParamList, 'AiSourceDetail'>;
type Detail = Record<string, unknown>;
const typeName: Record<string, string> = { PERSON: 'Thành viên', POST: 'Bài đăng', STORY: 'Câu chuyện', DOCUMENT: 'Tài liệu', EVENT: 'Sự kiện' };
function endpoint(type: string, id: string) {
  switch (type) {
    case 'PERSON': return `/persons/${id}`;
    case 'POST': return `/posts/${id}`;
    case 'STORY': return `/heritage/stories/${id}`;
    case 'DOCUMENT': return `/heritage/documents/${id}`;
    case 'EVENT': return `/events/${id}`;
    default: return null;
  }
}
function textField(value: unknown): string | null {
  return typeof value === 'string' && value.trim() ? value : null;
}

export function AiSourceDetailScreen({ route }: Props) {
  const { sourceType, sourceId, title: fallbackTitle, snippet } = route.params;
  const [detail, setDetail] = useState<Detail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [summary, setSummary] = useState<string | null>(null);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [summarizing, setSummarizing] = useState(false);
  const canSummarize = sourceType === 'POST' || sourceType === 'STORY';

  async function load() {
    const path = endpoint(sourceType, sourceId);
    if (!path) { setLoadError('Không hỗ trợ mở loại nguồn này.'); setLoading(false); return; }
    setLoading(true); setLoadError(null);
    try { setDetail(await api.get<Detail>(path)); }
    catch (cause) { setLoadError(cause instanceof ApiError ? cause.message : 'Không tải được nội dung.'); }
    finally { setLoading(false); }
  }
  useEffect(() => { void load(); }, [sourceType, sourceId]);

  async function summarize() {
    if (!canSummarize) return;
    setSummarizing(true); setSummaryError(null);
    try { setSummary((await aiApi.summarize(sourceType, sourceId)).summary); }
    catch (cause) { setSummaryError(cause instanceof ApiError ? cause.message : 'Chưa tóm tắt được. Vui lòng thử lại.'); }
    finally { setSummarizing(false); }
  }

  const title = textField(detail?.title) ?? textField(detail?.fullName) ?? fallbackTitle ?? typeName[sourceType];
  const content = textField(detail?.content) ?? textField(detail?.description) ?? textField(detail?.snippet) ?? snippet;

  return (
    <ScrollView contentContainerStyle={{ padding: spacing.page, paddingBottom: 96, gap: 16 }}>
      <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>{title}</Text>
      {loading ? <Loading label="Đang tải chi tiết…" /> : null}
      {loadError ? <><Notice variant="error" title="Không mở được chi tiết" message={loadError} /><Button title="Thử tải lại" variant="secondary" onPress={() => void load()} /></> : null}
      {!loading && detail ? <Card title={typeName[sourceType]}>
        {content ? <Text style={[text.body, { color: colors.textPrimary }]}>{content}</Text> : <Text style={[text.body, { color: colors.textSecondary }]}>Chưa có nội dung chi tiết.</Text>}
        {textField(detail.authorName) ? <Text style={[text.caption, { color: colors.textSecondary }]}>Đăng bởi {String(detail.authorName)}</Text> : null}
      </Card> : null}
      {canSummarize ? <View style={{ gap: 10 }}>
        <Button title="Tóm tắt bằng AI" onPress={() => void summarize()} loading={summarizing} disabled={loading || Boolean(loadError)} />
        {summaryError ? <><Notice variant="error" title="Tóm tắt chưa hoàn tất" message={summaryError} /><Button title="Thử lại" variant="secondary" onPress={() => void summarize()} /></> : null}
        {summary ? <Card title="Tóm tắt AI"><Text style={[text.body, { color: colors.textPrimary }]}>{summary}</Text></Card> : null}
      </View> : null}
    </ScrollView>
  );
}
