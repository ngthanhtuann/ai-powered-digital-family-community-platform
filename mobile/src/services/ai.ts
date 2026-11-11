import { api } from './api';

export type AiSourceType = 'PERSON' | 'POST' | 'STORY' | 'DOCUMENT' | 'EVENT';
export type AiSource = { sourceType: AiSourceType; sourceId: string };
export type AiSearchResult = AiSource & {
  title: string;
  snippet: string;
  path: string;
  score: number;
};
export type AiChatReply = {
  sessionId: string;
  messageId: string;
  answer: string;
  sources: AiSource[];
  latencyMs: number;
};
export type AiSummary = { summary: string; sourceType: string; sourceId: string; cached: boolean };

const AI_TIMEOUT_MS = 45_000;

export const aiApi = {
  chat: (question: string, sessionId: string | null) =>
    api.post<AiChatReply>('/ai/chat', { question, sessionId }, { timeout: AI_TIMEOUT_MS }),
  search: (query: string, page = 0, size = 20, familyId?: string) =>
    api.get<{ query: string; results: AiSearchResult[]; page: number; size: number; total: number; message?: string }>(
      '/ai/search', { q: query, page, size, ...(familyId ? { familyId } : {}) }, { timeout: AI_TIMEOUT_MS },
    ),
  summarize: (sourceType: 'POST' | 'STORY', sourceId: string) =>
    api.post<AiSummary>('/ai/summaries', { sourceType, sourceId, maxLength: 500 }, { timeout: AI_TIMEOUT_MS }),
};
