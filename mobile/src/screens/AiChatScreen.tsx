import { useEffect, useRef, useState } from 'react';
import {
  FlatList,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { DrawerNavigationProp } from '@react-navigation/drawer';
import { ApiError } from '../services/api';
import { useSession } from '../session/SessionContext';
import { aiApi, type AiSource } from '../services/ai';
import { Button, Card, Notice } from '../components/ui';
import { colors, radius, spacing, text } from '../theme';
import type { AppDrawerParamList } from '../navigation/types';

type Message = { id: string; role: 'user' | 'assistant'; text: string; sources?: AiSource[] };
const SUGGESTIONS = [
  'Gia đình mình có những nhánh nào?',
  'Tóm tắt lịch sử gia đình',
  'Ai là người lớn tuổi nhất trong gia đình?',
];
const SOURCE_LABEL: Record<AiSource['sourceType'], string> = {
  PERSON: 'Thành viên',
  POST: 'Bài đăng',
  STORY: 'Câu chuyện',
  DOCUMENT: 'Tài liệu',
  EVENT: 'Sự kiện',
  PHOTO: 'Ảnh di sản',
};

export function AiChatScreen() {
  const navigation = useNavigation<DrawerNavigationProp<AppDrawerParamList>>();
  const { session } = useSession();
  // SYSTEM_ADMIN không thuộc gia đình nào; /ai/chat của quản trị cần chọn gia đình, chỉ có trên bản web.
  const isSystemAdmin =
    session.status === 'signedIn' && session.user.roles.includes('SYSTEM_ADMIN');
  const [messages, setMessages] = useState<Message[]>([]);
  const [question, setQuestion] = useState('');
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [retryQuestion, setRetryQuestion] = useState<string | null>(null);
  const listRef = useRef<FlatList<Message>>(null);
  // Tăng khi bấm "Cuộc trò chuyện mới" để bỏ câu trả lời của cuộc cũ còn đang chờ (giống bản web).
  const conversationVersion = useRef(0);

  useEffect(() => {
    if (messages.length)
      requestAnimationFrame(() => listRef.current?.scrollToEnd({ animated: true }));
  }, [messages, pending]);

  async function send(raw: string) {
    const value = raw.trim();
    if (!value || pending || isSystemAdmin) return;
    const userMessage: Message = { id: `user-${Date.now()}`, role: 'user', text: value };
    setMessages((current) => [...current, userMessage]);
    setQuestion('');
    setError(null);
    setPending(true);
    setRetryQuestion(value);
    const version = conversationVersion.current;
    try {
      const reply = await aiApi.chat(value, sessionId);
      if (version !== conversationVersion.current) return;
      setSessionId(reply.sessionId ?? null);
      setMessages((current) => [
        ...current,
        {
          id: reply.messageId ?? `assistant-${Date.now()}`,
          role: 'assistant',
          text: reply.answer,
          sources: reply.sources,
        },
      ]);
      setRetryQuestion(null);
    } catch (cause) {
      if (version !== conversationVersion.current) return;
      const message =
        cause instanceof ApiError
          ? cause.message
          : 'Chưa nhận được câu trả lời. Bạn có thể thử lại.';
      setError(message);
      setMessages((current) => current.filter((item) => item.id !== userMessage.id));
    } finally {
      if (version === conversationVersion.current) setPending(false);
    }
  }

  function newConversation() {
    conversationVersion.current += 1;
    setPending(false);
    setMessages([]);
    setSessionId(null);
    setError(null);
    setRetryQuestion(null);
    setQuestion('');
  }

  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 20}
    >
      <View style={{ flex: 1, padding: spacing.page, gap: 12 }}>
        <View
          style={{ flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' }}
        >
          <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>Trợ lý AI</Text>
          <Button title="Cuộc trò chuyện mới" variant="secondary" onPress={newConversation} />
        </View>
        {isSystemAdmin ? (
          <Notice
            variant="info"
            title="Dùng bản web để hỏi"
            message="Tài khoản quản trị hệ thống cần chọn gia đình trước khi hỏi trợ lý; việc này hiện chỉ có trên bản web."
          />
        ) : null}
        {error ? <Notice variant="error" title="Chưa gửi được câu hỏi" message={error} /> : null}
        {error && retryQuestion ? (
          <Button title="Thử lại" variant="secondary" onPress={() => send(retryQuestion)} />
        ) : null}
        <FlatList
          ref={listRef}
          style={{ flex: 1 }}
          contentContainerStyle={{ gap: 12, paddingBottom: 12 }}
          keyboardShouldPersistTaps="handled"
          data={messages}
          keyExtractor={(item) => item.id}
          ListEmptyComponent={
            <Card
              title="Bạn muốn tìm hiểu điều gì?"
              description="Hỏi về gia đình, thành viên, câu chuyện hoặc tài liệu."
            >
              {SUGGESTIONS.map((suggestion) => (
                <Pressable
                  key={suggestion}
                  onPress={() => send(suggestion)}
                  style={{ paddingVertical: 8 }}
                >
                  <Text style={[text.body, { color: colors.info }]}>{suggestion}</Text>
                </Pressable>
              ))}
            </Card>
          }
          renderItem={({ item }) => (
            <View
              style={{
                alignSelf: item.role === 'user' ? 'flex-end' : 'stretch',
                maxWidth: '92%',
                backgroundColor: item.role === 'user' ? colors.infoSoft : colors.surface,
                borderRadius: radius.card,
                padding: 14,
                gap: 10,
              }}
            >
              <Text style={[text.body, { color: colors.textPrimary }]}>{item.text}</Text>
              {item.sources?.length ? (
                <View style={{ gap: 6 }}>
                  <Text style={[text.caption, { color: colors.textSecondary }]}>
                    Nguồn tham khảo
                  </Text>
                  {item.sources.map((source) => (
                    <Pressable
                      key={`${source.sourceType}-${source.sourceId}`}
                      onPress={() => navigation.navigate('AiSourceDetail', source)}
                    >
                      <Text
                        style={[
                          text.caption,
                          { color: colors.info, textDecorationLine: 'underline' },
                        ]}
                      >
                        {SOURCE_LABEL[source.sourceType]} · Mở chi tiết
                      </Text>
                    </Pressable>
                  ))}
                </View>
              ) : null}
            </View>
          )}
          ListFooterComponent={
            pending ? (
              <View style={{ padding: 10 }}>
                <Text style={[text.body, { color: colors.textSecondary }]}>AI đang trả lời…</Text>
              </View>
            ) : undefined
          }
          onContentSizeChange={() => listRef.current?.scrollToEnd({ animated: true })}
        />
        <View style={{ flexDirection: 'row', alignItems: 'flex-end', gap: 8 }}>
          <TextInput
            accessibilityLabel="Câu hỏi cho trợ lý AI"
            value={question}
            onChangeText={setQuestion}
            onSubmitEditing={() => send(question)}
            placeholder="Nhập câu hỏi…"
            placeholderTextColor={colors.textSecondary}
            multiline
            maxLength={4000}
            editable={!pending && !isSystemAdmin}
            style={{
              flex: 1,
              maxHeight: 120,
              minHeight: 44,
              borderWidth: 1,
              borderColor: colors.border,
              borderRadius: radius.control,
              backgroundColor: colors.surface,
              padding: 12,
              color: colors.textPrimary,
            }}
          />
          <Button
            title="Gửi"
            onPress={() => send(question)}
            loading={pending}
            disabled={!question.trim() || isSystemAdmin}
          />
        </View>
      </View>
    </KeyboardAvoidingView>
  );
}
