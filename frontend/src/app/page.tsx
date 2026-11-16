'use client';

import Link from 'next/link';
import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { BookOpen, Calendar, Maximize2, MessageSquare, Search, Users, type LucideIcon } from 'lucide-react';
import { AccessState, useHeritageAccess } from '@/components/heritage/HeritageAccess';
import { Card } from '@/components/ui/Card';
import { FAMILY_TIME_ZONE, formatDate } from '@/lib/datetime';
import { communityApi } from '@/services/community/posts';
import { eventsApi, type Event } from '@/services/events/events';
import { genealogyApi, type TreeData, type TreeNode } from '@/services/genealogy';
import { heritageApi } from '@/services/heritage';
import { notificationsApi } from '@/services/notifications';

const DAY = 24 * 60 * 60 * 1000;
// Sự kiện dòng họ (giỗ, họp mặt) thường báo trước vài tháng.
const UPCOMING_DAYS = 90;
const dayPart = new Intl.DateTimeFormat('vi-VN', { timeZone: FAMILY_TIME_ZONE, day: '2-digit' });
const monthPart = new Intl.DateTimeFormat('vi-VN', { timeZone: FAMILY_TIME_ZONE, month: 'numeric' });
const longDate = new Intl.DateTimeFormat('vi-VN', {
  timeZone: FAMILY_TIME_ZONE, weekday: 'long', day: 'numeric', month: 'long', year: 'numeric',
});

/**
 * Tổng quan (Figma page 05, khung "Tổng quan / 1440, 768, 360"): số liệu nhanh, cây gia phả thu gọn,
 * lối vào tra cứu xưng hô, thành viên mới thêm và sự kiện sắp tới. Chỉ dùng API mọi thành viên được gọi.
 */
export default function HomePage() {
  const access = useHeritageAccess();
  const familyId = access.familyId ?? undefined;
  const ready = access.isReady && Boolean(familyId);
  // Mốc thời gian cố định trong một lần mở trang, để queryKey không đổi mỗi lần render.
  const now = useMemo(() => Date.now(), []);

  const events = useQuery({
    queryKey: ['home-events', familyId],
    queryFn: () => eventsApi.list(0, new Date(now).toISOString(), new Date(now + UPCOMING_DAYS * DAY).toISOString(), familyId),
    enabled: ready,
  });
  const unread = useQuery({ queryKey: ['home-unread'], queryFn: () => notificationsApi.list(0, false), enabled: ready });
  const posts = useQuery({ queryKey: ['home-posts', familyId], queryFn: () => communityApi.posts(0, undefined, familyId), enabled: ready });
  const stories = useQuery({
    queryKey: ['home-stories', familyId],
    queryFn: () => heritageApi.stories({ page: 0, size: 1, familyId }),
    enabled: ready,
  });
  const members = useQuery({
    queryKey: ['home-members', familyId],
    queryFn: () => genealogyApi.persons(familyId ?? '', { page: 0, size: 3, sort: 'createdAt,desc' }),
    enabled: ready,
  });
  const tree = useQuery({ queryKey: ['tree', familyId], queryFn: () => genealogyApi.tree(familyId ?? ''), enabled: ready });

  const weekPosts = posts.data?.items.filter((post) => now - new Date(post.createdAt).getTime() <= 7 * DAY) ?? [];
  const postsCapped = posts.data && weekPosts.length === posts.data.items.length && posts.data.meta.total > weekPosts.length;

  return (
    <>
      <h1 className="text-page-title lg:sr-only">Tổng quan</h1>
      <div className="space-y-6">
      <AccessState loading={access.membership.isLoading || access.profile.isLoading}
        error={(access.membership.error as Error | null)?.message} />
      {access.isReady && !familyId && (
        <Card title="Chưa chọn gia đình"
          description="Tài khoản quản trị hệ thống không thuộc gia đình nào. Mở Thống kê hoặc Quản trị để xem từng gia đình." />
      )}

      {ready && (
        <>
          <section aria-label="Số liệu nhanh" className="grid grid-cols-2 gap-4 lg:grid-cols-4">
            <Stat label="Sự kiện sắp tới" icon={Calendar} tone="bg-warning-soft" query={events}
              value={events.data?.meta.total} note={`Trong ${UPCOMING_DAYS} ngày tới`} noteClass="text-success" />
            <Stat label="Thông báo chưa đọc" icon={MessageSquare} tone="bg-success-soft" query={unread}
              value={unread.data?.meta.total} note="Mở bằng chuông thông báo" />
            <Stat label="Bài đăng mới" icon={Users} tone="bg-info-soft" query={posts}
              value={posts.data ? `${weekPosts.length}${postsCapped ? '+' : ''}` : undefined} note="Trong 7 ngày qua" />
            <Stat label="Câu chuyện" icon={BookOpen} tone="bg-error-soft" query={stories}
              value={stories.data?.meta.total} note="Lịch sử dòng họ" noteClass="text-error" />
          </section>

          <div className="grid gap-5 lg:grid-cols-2">
            <Card title="Cây gia phả" description="Người gần bạn nhất trong cây"
              actions={<ActionLink href="/tree" icon={Maximize2}>Xem đầy đủ</ActionLink>}>
              <TreePreview query={tree} />
            </Card>

            <section aria-labelledby="home-relationship" className="overflow-hidden rounded-card bg-surface shadow-overlay">
              <div className="flex items-center justify-between gap-3 bg-primary px-5 py-4">
                <div className="min-w-0">
                  <h2 id="home-relationship" className="flex items-center gap-2 text-label text-cream-on-dark">
                    <Search aria-hidden="true" size={16} strokeWidth={1.5} /> Tra cứu cách xưng hô
                  </h2>
                  <p className="mt-1 text-caption text-cream-on-dark/80">Chọn hai người trong dòng họ để biết gọi nhau thế nào</p>
                </div>
                <Link href="/relationship"
                  className="inline-flex min-h-11 shrink-0 items-center rounded-control bg-secondary px-4 text-label text-text-primary hover:bg-gold-hover">
                  Tra cứu
                </Link>
              </div>
              <div className="space-y-2 p-5">
                <p className="text-label">Bảng cách xưng hô nhanh</p>
                <table className="w-full overflow-hidden rounded-control border border-border text-left text-caption">
                  <thead className="bg-background">
                    <tr><th scope="col" className="px-3 py-2 font-medium">Quan hệ</th><th scope="col" className="px-3 py-2 font-medium">Gọi là</th><th scope="col" className="px-3 py-2 font-medium">Xưng là</th></tr>
                  </thead>
                  <tbody>
                    {QUICK_TERMS.map(([relation, call, self]) => (
                      <tr key={relation} className="border-t border-border">
                        <td className="px-3 py-2">{relation}</td>
                        <td className="px-3 py-2 font-medium text-error">{call}</td>
                        <td className="px-3 py-2 font-medium text-error">{self}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>

            <Card title="Thành viên mới thêm" actions={<ActionLink href="/members">Xem tất cả</ActionLink>}>
              <QueryState query={members} empty={!members.data?.items.length} emptyText="Chưa có thành viên.">
                <ul className="space-y-3">
                  {members.data?.items.map((person) => (
                    <li key={person.id}>
                      {/* Cả dòng là vùng bấm (≥ 44px), không chỉ dòng tên. */}
                      <Link href={`/persons/${person.id}`}
                        className="flex min-h-11 items-center gap-3 rounded-card bg-surface-muted px-3 py-2 hover:bg-background">
                        <Initial name={person.fullName ?? ''} className={genderTone(person.gender)} />
                        <span className="min-w-0">
                          <span className="block truncate text-label">{person.fullName}</span>
                          <span className="block text-caption text-text-secondary">
                            {[person.birthYear && `Sinh ${person.birthYear}`, person.createdAt && `Thêm ngày ${formatDate(person.createdAt)}`]
                              .filter(Boolean).join(' • ')}
                          </span>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              </QueryState>
            </Card>

            <Card title="Sự kiện và ngày giỗ sắp tới" actions={<ActionLink href="/events">Xem lịch</ActionLink>}>
              <QueryState query={events} empty={!events.data?.items.length} emptyText={`Không có sự kiện nào trong ${UPCOMING_DAYS} ngày tới.`}>
                <ul className="space-y-3">
                  {events.data?.items.slice(0, 3).map((event) => <EventRow key={event.id} event={event} now={now} />)}
                </ul>
              </QueryState>
            </Card>
          </div>
        </>
      )}
    </div>
    </>
  );
}

const QUICK_TERMS: [string, string, string][] = [
  ['Con với bố mẹ', 'Bố / Mẹ', 'Con'],
  ['Cháu với ông bà', 'Ông / Bà', 'Cháu'],
  ['Em với anh chị', 'Anh / Chị', 'Em'],
];

type Loadable = { isPending: boolean; isError: boolean; error: unknown };

function QueryState({ query, empty, emptyText, children }: {
  query: Loadable; empty: boolean; emptyText: string; children: React.ReactNode;
}) {
  if (query.isPending) return <p className="text-body text-text-secondary">Đang tải...</p>;
  if (query.isError) {
    return <p role="alert" className="text-body text-error">{query.error instanceof Error ? query.error.message : 'Không tải được.'}</p>;
  }
  if (empty) return <p className="text-body text-text-secondary">{emptyText}</p>;
  return <>{children}</>;
}

function Stat({ label, icon: Icon, tone, query, value, note, noteClass = 'text-text-secondary' }: {
  label: string; icon: LucideIcon; tone: string; query: Loadable; value?: number | string; note: string; noteClass?: string;
}) {
  return (
    <div className="space-y-2 rounded-card border border-border bg-surface p-4 shadow-card md:p-5">
      <div className="flex items-center justify-between gap-2">
        <p className="text-caption font-medium text-text-secondary">{label}</p>
        <span aria-hidden="true" className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-control ${tone}`}>
          <Icon size={16} strokeWidth={1.5} />
        </span>
      </div>
      <p className="text-metric">{query.isError ? '—' : (value ?? '…')}</p>
      <p className={`text-caption ${noteClass}`}>{note}</p>
    </div>
  );
}

function ActionLink({ href, icon: Icon, children }: { href: string; icon?: LucideIcon; children: React.ReactNode }) {
  return (
    <Link href={href}
      className="inline-flex min-h-11 items-center gap-2 rounded-control border border-secondary px-3 text-label hover:bg-surface-muted">
      {Icon && <Icon aria-hidden="true" size={14} strokeWidth={1.5} />}
      {children}
    </Link>
  );
}

function Initial({ name, className, size = 'h-10 w-10' }: { name: string; className: string; size?: string }) {
  const letter = name.trim().split(/\s+/).pop()?.[0]?.toUpperCase() ?? '?';
  return (
    <span aria-hidden="true" className={`flex ${size} shrink-0 items-center justify-center rounded-full border-2 border-surface text-label text-text-primary ${className}`}>
      {letter}
    </span>
  );
}

// Màu theo chú thích của Figma: nam vàng, nữ hồng nhạt; giới tính bị ẩn (trẻ em, BR-GEN-10) dùng màu trung tính.
function genderTone(gender?: string | null) {
  if (gender === 'MALE') return 'bg-secondary';
  if (gender === 'FEMALE') return 'bg-accent-pink-soft';
  return 'bg-border';
}

function TreePreview({ query }: { query: Loadable & { data?: TreeData } }) {
  const { root, spouse, children } = useMemo(() => {
    const nodes = query.data?.nodes ?? [];
    const edges = query.data?.edges ?? [];
    const byId = new Map(nodes.map((n) => [n.id, n]));
    const rootNode = byId.get(query.data?.rootId) ?? nodes[0];
    const spouseEdge = edges.find((e) => e.type === 'MARRIAGE' && (e.source === rootNode?.id || e.target === rootNode?.id));
    const spouseId = spouseEdge && (spouseEdge.source === rootNode?.id ? spouseEdge.target : spouseEdge.source);
    const kids = edges.filter((e) => e.type === 'PARENT_CHILD' && e.source === rootNode?.id)
      .map((e) => byId.get(e.target)).filter((n): n is TreeNode => Boolean(n)).slice(0, 3);
    return { root: rootNode, spouse: spouseId ? byId.get(spouseId) : undefined, children: kids };
  }, [query.data]);

  return (
    <QueryState query={query} empty={!root} emptyText="Gia đình chưa có thành viên.">
      <figure aria-label="Cây gia phả thu gọn" className="rounded-card bg-surface-muted px-3 py-5">
        <div className="flex justify-center gap-6">
          {[root, spouse].filter((n): n is TreeNode => Boolean(n)).map((node) => <TreePerson key={node.id} node={node} big />)}
        </div>
        {children.length > 0 && (
          <>
            <span aria-hidden="true" className="mx-auto block h-5 w-0.5 bg-secondary" />
            <div className="flex flex-wrap justify-center gap-4">
              {children.map((node) => <TreePerson key={node.id} node={node} child />)}
            </div>
          </>
        )}
      </figure>
      <ul aria-label="Chú thích màu" className="mt-3 flex flex-wrap gap-4 text-caption text-text-secondary">
        <li className="flex items-center gap-1.5"><span aria-hidden="true" className="h-2.5 w-2.5 rounded-full bg-secondary" />Nam</li>
        <li className="flex items-center gap-1.5"><span aria-hidden="true" className="h-2.5 w-2.5 rounded-full bg-accent-pink-soft" />Nữ</li>
        <li className="flex items-center gap-1.5"><span aria-hidden="true" className="h-2.5 w-2.5 rounded-full bg-accent-green" />Con cái</li>
      </ul>
    </QueryState>
  );
}

function TreePerson({ node, big = false, child = false }: { node: TreeNode; big?: boolean; child?: boolean }) {
  return (
    <div className="flex w-28 flex-col items-center gap-1 text-center">
      {child && <span aria-hidden="true" className="h-3 w-0.5 bg-secondary" />}
      <Initial name={node.fullName ?? ''} size={big ? 'h-12 w-12' : 'h-10 w-10'}
        className={child ? 'bg-accent-green text-surface' : genderTone(node.gender)} />
      <p className="text-caption text-text-primary">{node.fullName}</p>
      {node.birthYear && <p className="text-caption text-text-secondary">Sinh {node.birthYear}</p>}
    </div>
  );
}

function EventRow({ event, now }: { event: Event; now: number }) {
  const start = new Date(event.startAt);
  const days = Math.max(0, Math.ceil((start.getTime() - now) / DAY));
  return (
    <li className="flex items-center gap-3 rounded-card border border-border bg-surface-muted px-3 py-3">
      <div aria-hidden="true" className="flex h-[52px] w-[52px] shrink-0 flex-col items-center justify-center rounded-card bg-primary text-cream-on-dark">
        <span className="text-section-title">{dayPart.format(start)}</span>
        <span className="text-caption">Th{monthPart.format(start)}</span>
      </div>
      <div className="min-w-0">
        <p className="truncate text-label">{event.title}</p>
        <p className="text-caption text-text-secondary">
          {longDate.format(start)}{event.location ? ` • ${event.location}` : ''}
        </p>
        <p className="text-caption text-text-primary">{days === 0 ? 'Hôm nay' : `Còn ${days} ngày nữa`}</p>
      </div>
    </li>
  );
}
