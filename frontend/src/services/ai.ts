import { api } from '@/lib/api';

export type SummarySourceType = 'POST' | 'STORY' | 'DOCUMENT';
export type AiSummary = { summary: string; sourceType: SummarySourceType; sourceId: string; cached: boolean };
export type AiSourceType = 'PERSON' | 'POST' | 'STORY' | 'DOCUMENT' | 'EVENT' | 'PHOTO';
export type AiSource = { sourceType: AiSourceType; sourceId: string };
export type AiChatResponse = {
  sessionId: string | null;
  messageId: string;
  answer: string;
  sources: AiSource[];
  latencyMs?: number;
};

export const aiApi = {
  summarize: (sourceType: SummarySourceType, sourceId: string) =>
    api.post<AiSummary>('/ai/summaries', { sourceType, sourceId }),
  chat: (question: string, sessionId: string | null) =>
    api.post<AiChatResponse>('/ai/chat', { question, sessionId }),
};
