'use client';

import { useQuery } from '@tanstack/react-query';
import Link from 'next/link';
import { useParams } from 'next/navigation';
import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Avatar } from '@/components/genealogy/PersonNode';
import { RecommendationsPanel } from '@/components/ai/RecommendationsPanel';
import { ApiError } from '@/lib/api';
import { formatDate, formatDateTime } from '@/lib/datetime';
import { genealogyApi } from '@/services/genealogy';
import { directoryApi } from '@/services/directory';
import { communityApi } from '@/services/community/posts';

const GENDER_LABEL: Record<string, string> = { MALE: 'Nam', FEMALE: 'Nữ', OTHER: 'Khác' };

/** 404 nghĩa là người đó chưa khai hồ sơ nghề nghiệp/học vấn, không phải lỗi. */
const optional = <T,>(load: () => Promise<T>) => () =>
  load().catch((error: unknown) => {
    if (error instanceof ApiError && error.status === 404) return null;
    throw error;
  });

/**
 * Trang cá nhân kiểu mạng xã hội nội bộ (SCRUM-117): ảnh, thông tin, nghề nghiệp, học vấn, vị trí trên gia phả
 * và bài đăng của người đó. Dữ liệu trẻ dưới 16 tuổi đã được backend giới hạn (BR-GEN-10, privacy.md).
 */
export default function PersonPage() {
  const { personId } = useParams<{ personId: string }>();
  const [page, setPage] = useState(0);
  const person = useQuery({ queryKey: ['person', personId], queryFn: () => genealogyApi.person(personId), retry: false });
  const p = person.data;
  const profession = useQuery({
    queryKey: ['profession-profile', personId],
    queryFn: optional(() => directoryApi.professionProfile(personId)),
    enabled: Boolean(p),
    retry: false,
  });
  const education = useQuery({
    queryKey: ['education-profile', personId],
    queryFn: optional(() => directoryApi.educationProfile(personId)),
    enabled: Boolean(p),
    retry: false,
  });
  const posts = useQuery({
    queryKey: ['person-posts', p?.userId, page],
    queryFn: () => communityApi.postsByAuthor(p!.userId!, page),
    enabled: Boolean(p?.userId),
  });

  if (person.isPending) return <p role="status" className="text-body text-text-secondary">Đang tải trang cá nhân...</p>;
  if (person.isError || !p) {
    return <p role="alert" className="text-body text-error">Không thể tải hồ sơ. {(person.error as Error | null)?.message}</p>;
  }

  const year = p.birthDate ? Number(p.birthDate.slice(0, 4)) : p.birthYear;
  const facts: [string, string | null | undefined][] = [
    ['Giới tính', p.gender ? GENDER_LABEL[p.gender] : null],
    ['Ngày sinh', p.birthDate ? formatDate(p.birthDate) : year ? `Năm ${year}` : null],
    ['Quê quán', p.birthplace],
    ['Nơi ở', [p.currentAddress, p.currentProvince].filter(Boolean).join(', ') || null],
    ['Ngày mất', p.isDeceased ? (p.deathDate ? formatDate(p.deathDate) : 'Đã mất') : null],
    ['Nơi an táng', p.burialPlace],
    ['Ghi chú', p.note],
  ];
  const job = profession.data;
  const school = education.data;
  const total = posts.data?.meta.total ?? 0;

  return (
    <div className="mx-auto max-w-4xl space-y-6">
      <section aria-labelledby="person-name" className="rounded-card border border-border bg-surface p-5 shadow-card">
        <div className="flex flex-wrap items-center gap-4">
          <Avatar mediaId={p.avatarMediaId} name={p.fullName ?? ''} size="h-24 w-24" />
          <div className="min-w-0 flex-1">
            <h1 id="person-name" className="text-page-title break-words">{p.fullName}</h1>
            <p className="text-body text-text-secondary">
              {[job?.jobTitle, p.currentProvince, year && `Sinh ${year}`].filter(Boolean).join(' · ') || 'Thành viên dòng họ'}
            </p>
          </div>
          <div className="flex flex-wrap gap-2">
            <Link href={`/families/${p.familyId}/tree?focus=${p.id}`}
              className="inline-flex min-h-11 items-center rounded-control bg-primary px-4 text-label text-cream-on-dark">
              Xem trên gia phả
            </Link>
            {p.canEdit && (
              <Link href={`/directory/members/${p.id}`}
                className="inline-flex min-h-11 items-center rounded-control border border-border px-4 text-label">
                Sửa nghề nghiệp, học vấn
              </Link>
            )}
          </div>
        </div>
      </section>

      <div className="grid gap-6 md:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
        <div className="space-y-6">
          <section aria-labelledby="gioi-thieu" className="space-y-3 rounded-card border border-border bg-surface p-5">
            <h2 id="gioi-thieu" className="text-section-title">Giới thiệu</h2>
            <dl className="space-y-2">
              {facts.filter(([, value]) => value).map(([label, value]) => (
                <div key={label}>
                  <dt className="text-caption text-text-secondary">{label}</dt>
                  <dd className="break-words text-body">{value}</dd>
                </div>
              ))}
            </dl>
          </section>
          <section aria-labelledby="nghe-hoc" className="space-y-3 rounded-card border border-border bg-surface p-5">
            <h2 id="nghe-hoc" className="text-section-title">Nghề nghiệp và học vấn</h2>
            {(profession.isPending || education.isPending) && <p className="text-body text-text-secondary">Đang tải...</p>}
            {job && (
              <p className="text-body">
                {job.jobTitle}{job.company ? ` tại ${job.company}` : ''}{job.workLocation ? ` (${job.workLocation})` : ''}
              </p>
            )}
            {school && (
              <p className="text-body">
                Học {school.school}{school.degree ? `, ${school.degree}` : ''}{school.graduationYear ? `, tốt nghiệp ${school.graduationYear}` : ''}
              </p>
            )}
            {profession.isSuccess && education.isSuccess && !job && !school && (
              <p className="text-body text-text-secondary">Chưa cập nhật.</p>
            )}
            {(profession.isError || education.isError) && (
              <p role="alert" className="text-body text-error">Không tải được nghề nghiệp, học vấn.</p>
            )}
          </section>
        </div>

        <section aria-labelledby="bai-dang" className="space-y-3">
          <h2 id="bai-dang" className="text-section-title">Bài đăng</h2>
          {!p.userId && <p className="rounded-card bg-surface p-5 text-body text-text-secondary">Người này chưa có tài khoản nên chưa có bài đăng.</p>}
          {posts.isPending && p.userId && <p className="text-body text-text-secondary">Đang tải bài đăng...</p>}
          {posts.isError && <p role="alert" className="text-body text-error">{(posts.error as Error).message}</p>}
          {posts.isSuccess && posts.data.items.length === 0 && (
            <p className="rounded-card bg-surface p-5 text-body text-text-secondary">Chưa có bài đăng bạn xem được.</p>
          )}
          <ul className="space-y-3">
            {posts.data?.items.map((post) => (
              <li key={post.id} className="rounded-card border border-border bg-surface p-4">
                <p className="text-caption text-text-secondary">
                  {formatDateTime(post.createdAt)} · {post.branchId ? 'Một chi' : 'Cả dòng họ'}
                </p>
                <p className="mt-2 whitespace-pre-wrap break-words text-body">{post.content}</p>
                <p className="mt-2 text-caption text-text-secondary">
                  {Object.values(post.reactionCounts ?? {}).reduce((sum, n) => sum + (n ?? 0), 0)} cảm xúc · {post.commentCount} bình luận
                  {' · '}<Link href={`/community#post-${post.id}`} className="text-info underline">Xem trên bảng tin</Link>
                </p>
              </li>
            ))}
          </ul>
          {total > 10 && (
            <nav aria-label="Phân trang bài đăng" className="flex items-center justify-between">
              <Button fullWidth={false} variant="secondary" disabled={page === 0} onClick={() => setPage(page - 1)}>Trang trước</Button>
              <span className="text-caption text-text-secondary">Trang {page + 1}/{Math.ceil(total / 10)}</span>
              <Button fullWidth={false} variant="secondary" disabled={(page + 1) * 10 >= total} onClick={() => setPage(page + 1)}>Trang sau</Button>
            </nav>
          )}
          <RecommendationsPanel contextType="PERSON" contextId={personId} />
        </section>
      </div>
    </div>
  );
}
