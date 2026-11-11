import { api } from '@/lib/api';

export type SummarySourceType = 'POST' | 'STORY' | 'DOCUMENT';
export type AiSummary = { summary: string; sourceType: SummarySourceType; sourceId: string; cached: boolean };
export type RelationshipExplanation = { found: boolean; explanation: string; relation: string | null; path: { id: string; fullName: string }[] };

export const aiApi = {
  summarize: (sourceType: SummarySourceType, sourceId: string) =>
    api.post<AiSummary>('/ai/summaries', { sourceType, sourceId }),
  explainRelationship: (personAId: string, personBId: string) =>
    api.post<RelationshipExplanation>('/ai/relationship-explanations', { personAId, personBId }),
};
