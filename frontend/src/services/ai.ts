import { api } from '@/lib/api';
import type { components, operations } from '@/services/generated/ai';

export type SummarySourceType = 'POST' | 'STORY' | 'DOCUMENT';
export type AiSummary = { summary: string; sourceType: SummarySourceType; sourceId: string; cached: boolean };
export type AiRecommendation = { sourceType: string; sourceId: string; title?: string; snippet?: string; score?: number; path?: string };
export type AiRecommendations = { recommendations: AiRecommendation[] };
// Kiểu sinh từ ai.yaml (npm run gen:api), không viết tay.
export type AiSource = components['schemas']['SourceReference'];
export type AiSourceType = AiSource['sourceType'];
type ChatOperation = operations['chatWithFamilyAssistant'];
export type AiChatRequest = ChatOperation['requestBody']['content']['application/json'];
export type AiChatResponse = ChatOperation['responses'][200]['content']['application/json']['data'];
export type RelationshipExplanation =
  operations['explainRelationship']['responses'][200]['content']['application/json']['data'];

export const aiApi = {
  summarize: (sourceType: SummarySourceType, sourceId: string) =>
    api.post<AiSummary>('/ai/summaries', { sourceType, sourceId }),
  /** familyId chỉ gửi khi SYSTEM_ADMIN chọn gia đình; vai trò khác backend tự lấy gia đình của mình. */
  chat: (question: string, sessionId: string | null, familyId?: string) =>
    api.post<AiChatResponse>('/ai/chat', {
      question, sessionId, ...(familyId ? { familyId } : {}),
    } satisfies AiChatRequest),
  explainRelationship: (personAId: string, personBId: string) =>
    api.post<RelationshipExplanation>('/ai/relationship-explanations', { personAId, personBId }),
  recommendations: (contextType: string, contextId: string, limit = 5) =>
    api.get<AiRecommendations>(`/ai/recommendations?contextType=${encodeURIComponent(contextType)}&contextId=${encodeURIComponent(contextId)}&limit=${limit}`),
};
