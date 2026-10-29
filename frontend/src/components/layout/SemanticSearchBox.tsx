'use client';

import { Search } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useState, type FormEvent } from 'react';

export function SemanticSearchBox({ systemAdmin = false }: { systemAdmin?: boolean }) {
  const router = useRouter();
  const [query, setQuery] = useState('');
  const [familyId, setFamilyId] = useState('');

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = query.trim();
    if (value) {
      const params = new URLSearchParams({ q: value });
      if (systemAdmin && familyId.trim()) params.set('familyId', familyId.trim());
      router.push(`/search?${params.toString()}`);
    }
  }

  return (
    <form onSubmit={submit} role="search" className="flex w-full max-w-2xl items-center gap-2">
      {systemAdmin && (
        <label className="sr-only" htmlFor="search-family-id">ID gia đình cần tìm</label>
      )}
      {systemAdmin && (
        <input id="search-family-id" value={familyId} onChange={(event) => setFamilyId(event.target.value)} placeholder="ID gia đình" className="min-h-11 w-36 rounded-control border border-border-strong bg-background px-2 text-caption" />
      )}
      <label className="sr-only" htmlFor="global-semantic-search">Tìm kiếm trong gia đình</label>
      <input
        id="global-semantic-search"
        value={query}
        onChange={(event) => setQuery(event.target.value)}
        maxLength={500}
        placeholder="Tìm người, bài đăng, tài liệu…"
        className="min-h-11 min-w-0 flex-1 rounded-control border border-border-strong bg-background px-3 text-body outline-none focus:border-info focus:ring-2 focus:ring-info/30"
      />
      <button
        type="submit"
        aria-label="Tìm kiếm"
        className="inline-flex min-h-11 items-center gap-2 rounded-control bg-secondary px-4 text-label text-text-primary hover:bg-gold-hover focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
      >
        <Search aria-hidden="true" size={18} />
        <span className="hidden sm:inline">Tìm</span>
      </button>
    </form>
  );
}
