import Image from 'next/image';
import Link from 'next/link';
import type { ReactNode } from 'react';

// Khung chung của màn Đăng nhập, Đăng ký theo Figma v2 (page "02 Auth và Admin", UC-AUTH-01, 02).
// Từ 1024px: chia đôi, trái là khối thương hiệu nền nâu, phải là thẻ form.
// Nhỏ hơn: dải thương hiệu ở trên (không có hình minh họa), thẻ form ngay dưới.
// Cỡ chữ khẩu hiệu 28/36, 32/40, 40/48 chưa có trong text style của design system (chờ TV5 thêm style "display").
interface AuthLayoutProps {
  title: string;
  description: string;
  switchPrompt: string;
  switchHref: string;
  switchLabel: string;
  children: ReactNode;
}

const FEATURES = [
  { icon: '/icons/network-gold.svg', text: 'Cây gia phả nhiều thế hệ, tra cứu quan hệ họ hàng' },
  { icon: '/icons/calendar-gold.svg', text: 'Bảng tin, sự kiện và kho di sản của dòng họ' },
  { icon: '/icons/sparkles-gold.svg', text: 'Trợ lý AI trả lời về lịch sử gia đình' },
];

function Brand() {
  return (
    <div className="flex items-center gap-3">
      <span className="flex size-9 items-center justify-center rounded-control bg-secondary">
        <Image src="/icons/network-dark.svg" alt="" width={20} height={20} unoptimized />
      </span>
      <span className="text-section-title text-cream-on-dark">FamilyConnect</span>
    </div>
  );
}

export function AuthLayout({
  title,
  description,
  switchPrompt,
  switchHref,
  switchLabel,
  children,
}: AuthLayoutProps) {
  return (
    <main className="flex min-h-screen flex-col bg-background lg:flex-row">
      <section
        aria-label="Giới thiệu FamilyConnect"
        className="relative flex flex-col gap-6 overflow-hidden bg-primary px-5 py-8 md:gap-8 md:px-14 md:py-12 lg:w-[47%] lg:max-w-[680px] lg:shrink-0 lg:justify-between lg:p-12 xl:p-16"
      >
        <Image
          src="/auth/rings.svg"
          alt=""
          width={560}
          height={560}
          unoptimized
          aria-hidden
          className="pointer-events-none absolute -right-[135px] -top-[135px] size-[300px] md:-right-[190px] md:-top-[190px] md:size-[420px] lg:-right-[260px] lg:-top-[240px] lg:size-[560px]"
        />
        <Brand />
        <div className="relative flex flex-col gap-8">
          <div className="flex flex-col gap-3 md:gap-4">
            <p className="text-[28px] font-bold leading-9 text-cream-on-dark md:text-[32px] md:leading-10 lg:text-[40px] lg:leading-[48px]">
              Kết nối <span className="text-secondary">các thế hệ</span>
              <br className="md:max-lg:hidden" /> trong gia đình
            </p>
            <p className="text-body text-cream-on-dark/80">
              Lưu giữ gia phả, kết nối người thân và gìn giữ ký ức của dòng họ, tất cả ở một nơi.
            </p>
          </div>
          {/* Hình minh họa trừu tượng các thế hệ: chỉ hiện khi màn hình đủ rộng và đủ cao */}
          <Image
            src="/auth/generations.svg"
            alt=""
            width={480}
            height={360}
            unoptimized
            aria-hidden
            className="hidden h-auto w-full max-w-[480px] lg:[@media(min-height:860px)]:block"
          />
          <ul className="hidden flex-col gap-3 lg:flex">
            {FEATURES.map((f) => (
              <li key={f.text} className="flex items-center gap-3 text-body text-cream-on-dark/90">
                <Image src={f.icon} alt="" width={20} height={20} unoptimized />
                {f.text}
              </li>
            ))}
          </ul>
        </div>
        <p className="hidden text-caption text-cream-on-dark/60 lg:block">© 2026 FamilyConnect</p>
      </section>

      <div className="flex flex-1 flex-col items-center gap-5 px-4 pb-8 pt-6 md:justify-center md:px-14 md:pt-10 lg:gap-6 lg:px-12 lg:py-10">
        <div className="flex w-full flex-col gap-6 rounded-card border border-border bg-surface p-6 shadow-card md:max-w-[520px] md:p-10 lg:max-w-[456px]">
          <div className="flex flex-col gap-2">
            <h1 className="text-page-title text-text-primary">{title}</h1>
            <p className="text-body text-text-secondary">{description}</p>
          </div>
          {children}
          <hr className="border-border" />
          <p className="text-center text-body text-text-secondary">
            {switchPrompt}{' '}
            <Link
              href={switchHref}
              className="rounded-control text-label text-text-primary underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
            >
              {switchLabel}
            </Link>
          </p>
        </div>
        <p className="flex w-full items-start gap-2 text-caption text-text-secondary md:max-w-[520px] md:items-center md:justify-center lg:max-w-none">
          <Image
            src="/icons/shield-check.svg"
            alt=""
            width={20}
            height={20}
            unoptimized
            className="shrink-0"
          />
          Chỉ thành viên đã được trưởng chi duyệt mới xem được dữ liệu gia đình.
        </p>
      </div>
    </main>
  );
}
