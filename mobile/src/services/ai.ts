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
  chat: (question: string, sessionId: string | null) =>
    api.post<AiChatReply>('/ai/chat', { question, sessionId }, { timeout: AI_TIMEOUT_MS }),
  search: (query: string, page = 0, size = 20, familyId?: string) =>
    api.get<SearchData>(
      '/ai/search',
      { q: query, page, size, ...(familyId ? { familyId } : {}) },
      { timeout: AI_TIMEOUT_MS },
    ),
  summarize: (sourceType: 'POST' | 'STORY', sourceId: string) =>
    api.post<AiSummary>('/ai/summaries', { sourceType, sourceId }, { timeout: AI_TIMEOUT_MS }),
};
