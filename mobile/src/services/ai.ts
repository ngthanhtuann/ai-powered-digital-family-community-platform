import { api } from './api';
import type { components, operations } from './generated/ai';

// Kiểu sinh từ docs/04-api/openapi/ai.yaml (npm run gen:api), không viết tay.
export type AiSource = components['schemas']['SourceReference'];
export type AiSourceType = AiSource['sourceType'];
export type AiSearchResult = components['schemas']['SearchResult'];
type ChatOperation = operations['chatWithFamilyAssistant'];
export type AiChatReply = ChatOperation['responses'][200]['content']['application/json']['data'];
export type AiSummary = { summary: string; sourceType: string; sourceId: string; cached: boolean };
type SearchData = {
  query: string;
  results: AiSearchResult[];
  page: number;
  size: number;
  total: number;
  message?: string | null;
};

// LLM có thể chậm hơn timeout mặc định của client.
const AI_TIMEOUT_MS = 45_000;

export const aiApi = {
  /** familyId chỉ gửi khi SYSTEM_ADMIN chọn gia đình; vai trò khác backend tự lấy gia đình của mình (như web). */
  chat: (question: string, sessionId: string | null, familyId?: string) =>
    api.post<AiChatReply>(
      '/ai/chat',
      { question, sessionId, ...(familyId ? { familyId } : {}) },
      { timeout: AI_TIMEOUT_MS },
    ),
  search: (query: string, page = 0, size = 20, familyId?: string) =>
    api.get<SearchData>(
      '/ai/search',
      { q: query, page, size, ...(familyId ? { familyId } : {}) },
      { timeout: AI_TIMEOUT_MS },
    ),
  summarize: (sourceType: 'POST' | 'STORY' | 'DOCUMENT', sourceId: string) =>
    api.post<AiSummary>('/ai/summaries', { sourceType, sourceId }, { timeout: AI_TIMEOUT_MS }),
  /** UC-AI-05: nội dung liên quan; không vượt phạm vi quyền xem (BR-AI-10). */
  recommendations: (contextType: AiRecommendationContext, contextId: string, limit = 5) =>
    api.get<AiRecommendations>(
      '/ai/recommendations',
      { contextType, contextId, limit },
      { timeout: AI_TIMEOUT_MS },
    ),
};

type RecommendationOperation = operations['getRecommendations'];
export type AiRecommendationContext = RecommendationOperation['parameters']['query']['contextType'];
export type AiRecommendations =
  RecommendationOperation['responses'][200]['content']['application/json']['data'];
