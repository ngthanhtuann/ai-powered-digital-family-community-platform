import { api } from '@/lib/api';
import type { operations } from '@/services/generated/ai';

export type SummarySourceType = 'POST' | 'STORY' | 'DOCUMENT';
export type AiSummary = { summary: string; sourceType: SummarySourceType; sourceId: string; cached: boolean };
// Kiểu sinh từ ai.yaml (npm run gen:api).
export type RelationshipExplanation =
  operations['explainRelationship']['responses'][200]['content']['application/json']['data'];

export const aiApi = {
  summarize: (sourceType: SummarySourceType, sourceId: string) =>
    api.post<AiSummary>('/ai/summaries', { sourceType, sourceId }),
  explainRelationship: (personAId: string, personBId: string) =>
    api.post<RelationshipExplanation>('/ai/relationship-explanations', { personAId, personBId }),
};
