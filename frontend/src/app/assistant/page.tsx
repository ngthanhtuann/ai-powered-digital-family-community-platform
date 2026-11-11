'use client';

import { useEffect, useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  ArrowUp, Bot, ChevronRight, CircleAlert, FileText, History, LoaderCircle, Plus, Sparkles, UserRound,
} from 'lucide-react';
import { useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { ApiError } from '@/lib/api';
import { aiApi, type AiSource, type AiSourceType } from '@/services/ai';
import { dashboardApi } from '@/services/dashboard';

type Message = { id: string; role: 'assistant' | 'user'; content: string; sources?: AiSource[]; failed?: boolean };

const SUGGESTIONS = [
  'Ông bà tổ tiên của gia đình mình có những ai?',
  'Kể cho tôi nghe về lịch sử dòng họ.',
  'Có những câu chuyện gia đình nào đáng nhớ?',
  'Ai là người lớn tuổi nhất trong gia đình?',
];

const SOURCE_LABEL: Record<AiSourceType, string> = {
  PERSON: 'Thành viên', POST: 'Bài viết', STORY: 'Câu chuyện', DOCUMENT: 'Tài liệu', EVENT: 'Sự kiện', PHOTO: 'Ảnh di sản',
};

/** Trang chi tiết của nguồn trích dẫn; bài đăng và sự kiện chưa có trang riêng nên mở danh sách. */
function sourceHref(source: AiSource) {
  const id = encodeURIComponent(source.sourceId);
  switch (source.sourceType) {
    case 'PERSON': return `/persons/${id}`;
    case 'STORY': return `/heritage/stories/${id}`;
    case 'DOCUMENT': return `/heritage/documents/${id}`;
    case 'PHOTO': return `/heritage/photos?mediaId=${id}`;
    case 'EVENT': return '/events';
    case 'POST': return '/community';
  }
}

/** Câu báo lỗi theo mã lỗi của API (ai.yaml): RATE_001 429, AI_001 503, AI_002 tính năng đang tắt. */
function readableError(error: unknown) {
  if (error instanceof ApiError) {
    if (error.code === 'RATE_001') {
      return error.retryAfter
        ? `Bạn đã hỏi quá nhiều trong thời gian ngắn. Thử lại sau khoảng ${Math.ceil(error.retryAfter / 60)} phút.`
        : 'Bạn đã hỏi quá nhiều trong thời gian ngắn. Vui lòng đợi một chút rồi thử lại.';
    }
    if (error.code === 'AI_001') return 'Trợ lý đang bận trong giây lát. Hãy thử lại nhé.';
    return error.message;
  }
  return 'Không gửi được câu hỏi. Kiểm tra kết nối rồi thử lại.';
}

/** SCRUM-63 (UC-AI-02): chat với trợ lý AI, có nguồn trích dẫn, câu hỏi gợi ý, cuộc trò chuyện mới, thử lại. */
export default function AssistantPage() {
  const access = useHeritageAccess();
  // SYSTEM_ADMIN không thuộc gia đình nào nên phải chọn gia đình để hỏi (ai.yaml: familyId bắt buộc với quản trị).
  const families = useQuery({
    queryKey: ['dashboard-families'], queryFn: dashboardApi.families, enabled: access.isSystemAdmin,
  });
  const [adminFamily, setAdminFamily] = useState('');
  const familyId = access.isSystemAdmin ? adminFamily || families.data?.[0]?.id || '' : undefined;
  const ready = access.isSystemAdmin ? Boolean(familyId) : access.isReady;

  const [messages, setMessages] = useState<Message[]>([]);
  const [question, setQuestion] = useState('');
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const endRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);
  // Tăng khi bấm "Cuộc trò chuyện mới" để bỏ qua câu trả lời của cuộc cũ còn đang chờ.
  const conversationVersion = useRef(0);

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages, pending]);

  async function ask(value: string) {
    const version = conversationVersion.current;
    setError('');
    setPending(true);
    try {
      const response = await aiApi.chat(value, sessionId, familyId);
      if (version !== conversationVersion.current) return;
      setSessionId(response.sessionId ?? sessionId);
      setMessages((current) => [...current, {
        id: response.messageId ?? `${Date.now()}-assistant`,
        role: 'assistant',
        content: response.answer,
        sources: response.sources ?? [],
      }]);
    } catch (caught) {
      if (version !== conversationVersion.current) return;
      setError(readableError(caught));
      setMessages((current) => [...current, {
        id: `${Date.now()}-failed`, role: 'assistant', content: 'Mình chưa thể trả lời câu hỏi này.', failed: true,
      }]);
    } finally {
      if (version === conversationVersion.current) setPending(false);
    }
  }

  function send(text = question) {
    const value = text.trim();
    if (!value || pending || !ready) return;
    setQuestion('');
    setMessages((current) => [...current, { id: `${Date.now()}-user`, role: 'user', content: value }]);
    void ask(value);
  }

  /** Gửi lại câu hỏi cuối: chỉ bỏ tin báo lỗi, giữ nguyên câu hỏi đã hiện để không bị lặp. */
  function retry() {
    const lastQuestion = [...messages].reverse().find((message) => message.role === 'user');
    if (!lastQuestion || pending) return;
    setMessages((current) => current.filter((message) => !message.failed));
    void ask(lastQuestion.content);
  }

  function newConversation() {
    conversationVersion.current += 1;
    setPending(false);
    setMessages([]);
    setSessionId(null);
    setError('');
    setQuestion('');
    inputRef.current?.focus();
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      send();
    }
  }

  function onSubmit(event: FormEvent) {
    event.preventDefault();
    send();
  }

  return (
    <div className="mx-auto flex min-h-[calc(100vh-13rem)] max-w-5xl flex-col overflow-hidden rounded-card border border-border bg-surface shadow-card lg:min-h-[calc(100vh-12rem)]">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-border px-4 py-3 sm:px-6">
        <div className="flex items-center gap-3">
          <div className="grid h-10 w-10 place-items-center rounded-full bg-primary text-cream-on-dark">
            <Sparkles size={19} />
          </div>
          <div>
            <h2 className="text-label font-semibold">Trợ lý gia đình</h2>
            <p className="text-caption text-text-secondary">Hỏi đáp dựa trên tư liệu dòng họ</p>
          </div>
        </div>
        <button
          type="button"
          onClick={newConversation}
          aria-label="Bắt đầu cuộc trò chuyện mới"
          className="inline-flex min-h-11 items-center gap-2 rounded-control border border-border px-3 text-label transition hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-info"
        >
          <Plus size={17} />
          <span className="hidden sm:inline">Cuộc trò chuyện mới</span>
          <span className="sm:hidden">Mới</span>
        </button>
        {access.isSystemAdmin && (
          <label className="grid w-full gap-1.5 text-label sm:w-72">
            Gia đình cần hỏi
            <select
              className="h-[42px] w-full rounded-control border border-border-strong bg-surface-muted px-3 text-body"
              value={familyId}
              disabled={families.isPending || !families.data?.length}
              onChange={(event) => { setAdminFamily(event.target.value); newConversation(); }}
            >
              {(families.data ?? []).map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}
            </select>
          </label>
        )}
      </div>

      <div className="flex flex-1 flex-col overflow-y-auto px-4 py-5 sm:px-8">
        {messages.length === 0 ? (
          <div className="m-auto w-full max-w-2xl py-8 text-center">
            <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-full bg-secondary/20 text-primary">
              <Bot size={28} />
            </div>
            <p className="text-section-title">Xin chào! Bạn muốn tìm hiểu điều gì?</p>
            <p className="mt-2 text-body text-text-secondary">
              Mình có thể giúp bạn khám phá thành viên, kỷ niệm và tư liệu gia đình.
            </p>
            <div className="mt-7 grid gap-3 text-left sm:grid-cols-2">
              {SUGGESTIONS.map((suggestion) => (
                <button
                  key={suggestion}
                  type="button"
                  disabled={pending || !ready}
                  onClick={() => send(suggestion)}
                  className="group flex min-h-[60px] items-center justify-between gap-3 rounded-card border border-border bg-surface p-4 text-body transition hover:border-secondary hover:bg-surface-muted focus-visible:outline-2 focus-visible:outline-info disabled:opacity-60"
                >
                  <span>{suggestion}</span>
                  <ChevronRight size={17} className="shrink-0 text-text-secondary transition group-hover:translate-x-0.5" />
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div className="mx-auto flex w-full max-w-3xl flex-col gap-6">
            {messages.map((message) => message.role === 'user' ? (
              <div key={message.id} className="flex items-start justify-end gap-2 sm:gap-3">
                <div className="max-w-[88%] whitespace-pre-wrap break-words rounded-2xl rounded-tr-sm bg-primary px-4 py-3 text-body text-cream-on-dark sm:max-w-[80%]">
                  {message.content}
                </div>
                <div className="mt-1 grid h-8 w-8 shrink-0 place-items-center rounded-full bg-secondary/30 text-primary">
                  <UserRound size={16} />
                </div>
              </div>
            ) : (
              <div key={message.id} className="flex items-start gap-2 sm:gap-3">
                <div className="mt-1 grid h-8 w-8 shrink-0 place-items-center rounded-full bg-secondary/20 text-primary">
                  <Bot size={17} />
                </div>
                <div className="min-w-0 max-w-[90%] rounded-2xl rounded-tl-sm border border-border bg-surface-muted px-4 py-3 sm:max-w-[82%]">
                  <p className="whitespace-pre-wrap break-words text-body leading-7">{message.content}</p>
                  {message.sources && message.sources.length > 0 && (
                    <div className="mt-4 border-t border-border pt-3">
                      <p className="mb-2 flex items-center gap-1.5 text-caption font-semibold text-text-secondary">
                        <History size={14} />Nguồn tham khảo
                      </p>
                      <div className="flex flex-wrap gap-2">
                        {message.sources.map((source, index) => (
                          <Link
                            key={`${source.sourceType}-${source.sourceId}-${index}`}
                            href={sourceHref(source)}
                            className="inline-flex min-h-9 items-center gap-1.5 rounded-full border border-border bg-surface px-3 py-1 text-caption text-info transition hover:border-info hover:bg-info-soft focus-visible:outline-2 focus-visible:outline-info"
                          >
                            <FileText size={13} />{SOURCE_LABEL[source.sourceType]} {index + 1}<ChevronRight size={13} />
                          </Link>
                        ))}
                      </div>
                    </div>
                  )}
                  {message.failed && (
                    <div className="mt-3 flex flex-wrap items-center gap-2 text-caption text-error">
                      <CircleAlert size={15} />{error}
                      <button
                        type="button"
                        onClick={retry}
                        disabled={pending}
                        className="ml-1 min-h-9 rounded-control border border-error px-3 font-medium hover:bg-error-soft focus-visible:outline-2 focus-visible:outline-info"
                      >
                        Thử lại
                      </button>
                    </div>
                  )}
                </div>
              </div>
            ))}
            {pending && (
              <div role="status" aria-live="polite" className="flex items-center gap-3 text-body text-text-secondary">
                <span className="grid h-8 w-8 place-items-center rounded-full bg-secondary/20 text-primary">
                  <LoaderCircle className="animate-spin" size={17} />
                </span>
                <span>Trợ lý đang trả lời…</span>
              </div>
            )}
            <div ref={endRef} />
          </div>
        )}
      </div>

      <div className="border-t border-border bg-surface px-4 py-3 sm:px-6 sm:py-4">
        <form onSubmit={onSubmit} className="mx-auto max-w-3xl">
          <label htmlFor="assistant-question" className="sr-only">Nhập câu hỏi</label>
          <div className="flex items-end gap-2 rounded-card border border-border-strong bg-surface p-2 shadow-card focus-within:border-primary focus-within:ring-1 focus-within:ring-primary">
            <textarea
              id="assistant-question"
              ref={inputRef}
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              onKeyDown={onKeyDown}
              rows={2}
              maxLength={4000}
              placeholder="Hỏi điều bạn muốn biết về gia đình…"
              className="max-h-36 min-h-11 flex-1 resize-y bg-transparent px-2 py-2 text-body outline-none placeholder:text-text-secondary/80"
            />
            <button
              type="submit"
              disabled={!question.trim() || pending || !ready}
              aria-label="Gửi câu hỏi"
              className="grid h-11 w-11 shrink-0 place-items-center rounded-control bg-primary text-cream-on-dark transition hover:bg-gold-hover hover:text-primary focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info disabled:cursor-not-allowed disabled:opacity-40"
            >
              <ArrowUp size={20} />
            </button>
          </div>
          <div className="mt-2 flex items-center justify-between gap-3 px-1 text-caption text-text-secondary">
            <span>Enter để gửi · Shift + Enter xuống dòng</span>
            <span>{question.length}/4000</span>
          </div>
        </form>
      </div>
    </div>
  );
}
