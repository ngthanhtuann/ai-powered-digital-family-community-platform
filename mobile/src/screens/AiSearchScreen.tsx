import { useState } from 'react';
import {
  Keyboard,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { DrawerNavigationProp } from '@react-navigation/drawer';
import { ApiError } from '../services/api';
import { aiApi, type AiSearchResult } from '../services/ai';
import { Button, Card, Notice } from '../components/ui';
import { useSession } from '../session/SessionContext';
import { colors, radius, spacing, text } from '../theme';
import type { AppDrawerParamList } from '../navigation/types';

const GROUPS: { title: string; types: AiSearchResult['sourceType'][] }[] = [
  { title: 'Thành viên', types: ['PERSON'] },
  { title: 'Bài đăng', types: ['POST'] },
  { title: 'Tài liệu và tư liệu', types: ['DOCUMENT', 'STORY', 'EVENT'] },
];

export function AiSearchScreen() {
  const navigation = useNavigation<DrawerNavigationProp<AppDrawerParamList>>();
  const { session } = useSession();
  const familyId =
    session.status === 'signedIn' ? (session.membership.familyId ?? undefined) : undefined;
  const isSystemAdmin =
    session.status === 'signedIn' && session.user.roles.includes('SYSTEM_ADMIN');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<AiSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [lastQuery, setLastQuery] = useState('');

  async function search(value = query) {
    const normalized = value.trim();
    if (!normalized || loading) return;
    Keyboard.dismiss();
    setLoading(true);
    setError(null);
    setLastQuery(normalized);
    try {
      const response = await aiApi.search(normalized, 0, 20, isSystemAdmin ? familyId : undefined);
      setResults(response.results ?? []);
    } catch (cause) {
      setError(cause instanceof ApiError ? cause.message : 'Chưa tìm kiếm được. Vui lòng thử lại.');
      setResults([]);
    } finally {
      setLoading(false);
    }
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 20}
    >
      <ScrollView
        keyboardShouldPersistTaps="handled"
        contentContainerStyle={{ padding: spacing.page, paddingBottom: 96, gap: 16 }}
      >
        <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>
          Tìm kiếm trong gia đình
        </Text>
        {isSystemAdmin && !familyId ? (
          <Notice
            variant="info"
            title="Cần chọn gia đình"
            message="Tài khoản quản trị hệ thống cần có gia đình được chọn để tìm kiếm."
          />
        ) : null}
        <View style={{ flexDirection: 'row', gap: 8, alignItems: 'flex-end' }}>
          <TextInput
            accessibilityLabel="Tìm kiếm ngữ nghĩa"
            value={query}
            onChangeText={setQuery}
            onSubmitEditing={() => search()}
            returnKeyType="search"
            placeholder="Ví dụ: Ai là ông nội của Minh?"
            placeholderTextColor={colors.textSecondary}
            style={{
              flex: 1,
              minHeight: 46,
              borderWidth: 1,
              borderColor: colors.border,
              borderRadius: radius.control,
              backgroundColor: colors.surface,
              paddingHorizontal: 12,
              color: colors.textPrimary,
            }}
          />
          <Button
            title="Tìm"
            onPress={() => search()}
            loading={loading}
            disabled={!query.trim() || (isSystemAdmin && !familyId)}
          />
        </View>
        {error ? <Notice variant="error" title="Tìm kiếm chưa hoàn tất" message={error} /> : null}
        {error && lastQuery ? (
          <Button title="Thử lại" variant="secondary" onPress={() => search(lastQuery)} />
        ) : null}
        {!loading && !error && lastQuery && !results.length ? (
          <Notice
            variant="info"
            title="Chưa tìm thấy kết quả"
            message="Thử mô tả câu hỏi bằng cách khác."
          />
        ) : null}
        {GROUPS.map((group) => {
          const items = results.filter((result) => group.types.includes(result.sourceType));
          if (!items.length) return null;
          return (
            <View key={group.title} style={{ gap: 10 }}>
              <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>{group.title}</Text>
              {items.map((item) => (
                <Pressable
                  key={`${item.sourceType}-${item.sourceId}`}
                  accessibilityRole="button"
                  onPress={() =>
                    navigation.navigate('AiSourceDetail', {
                      sourceType: item.sourceType,
                      sourceId: item.sourceId,
                      title: item.title,
                      snippet: item.snippet,
                    })
                  }
                >
                  <Card title={item.title}>
                    <Text style={[text.body, { color: colors.textSecondary }]}>{item.snippet}</Text>
                    {item.path ? (
                      <Text style={[text.caption, { color: colors.info }]}>{item.path}</Text>
                    ) : null}
                  </Card>
                </Pressable>
              ))}
            </View>
          );
        })}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}
