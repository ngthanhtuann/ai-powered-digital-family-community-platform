import { api } from '@/lib/api';

export type SemanticSearchResult = {
  sourceType: 'PERSON' | 'POST' | 'STORY' | 'DOCUMENT' | 'EVENT';
  sourceId: string;
  title: string;
  snippet: string;
  score: number;
  path: string;
};

export type SemanticSearchResponse = {
  query: string;
  results: SemanticSearchResult[];
  page: number;
  size: number;
  total: number;
  message: string | null;
};

export const semanticSearch = (q: string, page = 0, familyId?: string) =>
  api.get<SemanticSearchResponse>('/ai/search', { q, page, size: 100, ...(familyId ? { familyId } : {}) });
