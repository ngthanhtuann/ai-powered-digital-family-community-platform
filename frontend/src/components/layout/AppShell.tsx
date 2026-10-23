'use client';

import {
  Archive,
  BookOpen,
  CalendarDays,
  ChartColumn,
  ContactRound,
  Database,
  GitFork,
  History,
  House,
  Image,
  LayoutDashboard,
  LockKeyhole,
  LogOut,
  Menu,
  MessageSquare,
  Search,
  ShieldCheck,
  SlidersHorizontal,
  Sparkles,
  UserCheck,
  UserCog,
  UserRound,
  Users,
  type LucideIcon,
} from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/Button';
import { getMyMembership, getMyProfile, logout } from '@/services/auth';
import { tokenStorage } from '@/lib/token-storage';
import {
  FAMILY_NAV_ITEM,
  NAV_SECTIONS,
  filterSectionsByRole,
  type AppRole,
  type NavItem,
} from '@/lib/nav';

const ICONS: Record<string, LucideIcon> = {
  archive: Archive,
  'book-open': BookOpen,
  'calendar-days': CalendarDays,
  'chart-column': ChartColumn,
  'contact-round': ContactRound,
  database: Database,
  network: GitFork,
  history: History,
  house: House,
  image: Image,
  'layout-dashboard': LayoutDashboard,
  'lock-keyhole': LockKeyhole,
  'log-out': LogOut,
  'message-square': MessageSquare,
  search: Search,
  'shield-check': ShieldCheck,
  'sliders-horizontal': SlidersHorizontal,
  sparkles: Sparkles,
  'user-check': UserCheck,
  'user-cog': UserCog,
  'user-round': UserRound,
  users: Users,
};

function isPublicRoute(pathname: string) {
  return pathname === '/login' || pathname === '/register';
}

function isCurrentRoute(pathname: string, href?: string) {
  if (!href) return false;
  return href === '/' ? pathname === '/' : pathname === href || pathname.startsWith(`${href}/`);
}

function MenuEntry({
  item,
  pathname,
  onNavigate,
}: {
  item: NavItem;
  pathname: string;
  onNavigate?: () => void;
}) {
  const Icon = ICONS[item.icon];
  const className = [
    'flex min-h-11 items-center gap-3 rounded-control px-3 py-2 text-body transition-colors',
    isCurrentRoute(pathname, item.href)
      ? 'bg-secondary font-semibold text-text-primary'
      : item.href
        ? 'text-cream-on-dark hover:bg-gold-hover hover:text-text-primary'
        : 'cursor-not-allowed text-cream-on-dark/60',
  ].join(' ');
  const icon = Icon ? <Icon aria-hidden="true" size={18} strokeWidth={1.75} /> : null;
  const content = (
    <>
      {icon}
      <span className="min-w-0 flex-1">{item.label}</span>
      {!item.href && <span className="text-caption">Sắp ra mắt</span>}
    </>
  );

  return item.href ? (
    <Link
      href={item.href}
      aria-current={isCurrentRoute(pathname, item.href) ? 'page' : undefined}
      className={className}
      onClick={onNavigate}
    >
      {content}
    </Link>
  ) : (
    <span aria-disabled="true" className={className}>
      {content}
    </span>
  );
}

function Navigation({
  role,
  pathname,
  familyName,
  isPending,
  onNavigate,
  id,
}: {
  role: AppRole;
  pathname: string;
  familyName: string;
  isPending: boolean;
  onNavigate?: () => void;
  id?: string;
}) {
  const sections = filterSectionsByRole(NAV_SECTIONS, role);
  const familyPath = isPending ? '/pending-approval' : '/join-family';

  return (
    <nav id={id} aria-label="Điều hướng chính" className="flex flex-1 flex-col gap-5">
      {role === 'UNVERIFIED' && (
        <div>
          <MenuEntry
            item={{ ...FAMILY_NAV_ITEM, href: familyPath }}
            pathname={pathname}
            onNavigate={onNavigate}
          />
        </div>
      )}
      {sections.map((section, index) => (
        <section key={section.heading ?? `top-${index}`}>
          {section.heading && (
            <h2 className="mb-1 px-3 text-caption font-semibold uppercase tracking-wide text-cream-on-dark/70">
              {section.heading}
            </h2>
          )}
          <ul className="flex flex-col gap-1">
            {section.items.map((item) => (
              <li key={item.label}>
                <MenuEntry item={item} pathname={pathname} onNavigate={onNavigate} />
              </li>
            ))}
          </ul>
        </section>
      ))}
      <div className="mt-auto border-t border-cream-on-dark/20 pt-4">
        <p className="mb-3 truncate px-3 text-caption text-cream-on-dark/80">
          {familyName || 'Chưa chọn gia đình'}
        </p>
        <Button
          variant="secondary"
          className="justify-start bg-transparent text-cream-on-dark hover:bg-gold-hover hover:text-text-primary"
          onClick={() => {
            onNavigate?.();
            void logout();
          }}
        >
          <LogOut aria-hidden="true" size={18} />
          Đăng xuất
        </Button>
      </div>
    </nav>
  );
}

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const [hasSession, setHasSession] = useState(false);
  const publicRoute = isPublicRoute(pathname);
  useEffect(() => {
    setHasSession(Boolean(tokenStorage.getAccess()));
  }, [pathname]);

  const profile = useQuery({
    queryKey: ['app-shell-profile'],
    queryFn: getMyProfile,
    enabled: hasSession && !publicRoute,
  });
  const membership = useQuery({
    queryKey: ['app-shell-membership'],
    queryFn: getMyMembership,
    enabled: hasSession && !publicRoute,
  });

  if (publicRoute) return children;

  const isSystemAdmin = profile.data?.roles.includes('SYSTEM_ADMIN') ?? false;
  const isPending = membership.data?.status === 'PENDING';
  const role: AppRole = isSystemAdmin
    ? 'SYSTEM_ADMIN'
    : membership.data?.status === 'APPROVED'
      ? membership.data.role === 'BRANCH_ADMIN'
        ? 'BRANCH_ADMIN'
        : 'MEMBER'
      : 'UNVERIFIED';
  const currentItem = NAV_SECTIONS.flatMap((section) => section.items).find((item) =>
    isCurrentRoute(pathname, item.href),
  );
  const pageTitle =
    role === 'UNVERIFIED'
      ? 'Gia đình'
      : pathname === '/ui-kit'
        ? 'Hướng dẫn UI kit'
        : (currentItem?.label ?? 'Tổng quan');
  const familyName = membership.data?.familyName ?? '';
  const accessError = profile.error ?? membership.error;

  return (
    <div className="min-h-screen lg:flex">
      <aside className="sticky top-0 hidden h-screen w-sidebar shrink-0 flex-col gap-6 overflow-y-auto bg-primary p-5 lg:flex">
        <Link
          href="/"
          className="rounded-control focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
        >
          <span className="block text-section-title text-cream-on-dark">FamilyConnect</span>
          <span className="mt-1 block truncate text-caption text-cream-on-dark/80">
            {familyName || 'Cộng đồng gia đình'}
          </span>
        </Link>
        <Navigation
          role={role}
          pathname={pathname}
          familyName={familyName}
          isPending={isPending}
          id="desktop-navigation"
        />
        <Link href="/ui-kit" className="text-center text-caption text-cream-on-dark underline">
          Hướng dẫn UI kit
        </Link>
      </aside>

      <div className="flex min-h-screen min-w-0 flex-1 flex-col">
        <div className="flex h-16 items-center justify-between bg-primary px-4 text-cream-on-dark lg:hidden">
          <Link href="/" className="min-w-0">
            <span className="block truncate text-section-title">FamilyConnect</span>
            <span className="block truncate text-caption text-cream-on-dark/80">
              {familyName || 'Cộng đồng gia đình'}
            </span>
          </Link>
          <a
            href="#mobile-navigation"
            className="inline-flex min-h-11 shrink-0 items-center gap-2 rounded-control px-3 text-label text-cream-on-dark focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-info"
          >
            <Menu aria-hidden="true" size={18} />
            Menu
          </a>
        </div>

        <header className="flex min-h-header flex-wrap items-center justify-between gap-3 border-b border-border bg-surface px-4 py-3 md:px-6 lg:px-8 lg:py-0">
          <div>
            <h1 className="text-page-title lg:text-section-title">{pageTitle}</h1>
            <p className="mt-1 text-body text-text-secondary">
              {familyName ||
                (role === 'UNVERIFIED'
                  ? 'Tham gia hoặc chờ duyệt vào gia đình'
                  : 'Không gian kết nối gia đình')}
            </p>
          </div>
          <div className="text-right">
            {profile.data && <p className="text-label">{profile.data.fullName}</p>}
            {profile.isLoading && (
              <p className="text-caption text-text-secondary">Đang tải tài khoản...</p>
            )}
          </div>
        </header>

        <main className="w-full flex-1 px-4 py-5 md:px-6 md:py-6 lg:px-8">
          <div className="mx-auto w-full max-w-[1440px]">
            {accessError && (
              <p
                role="alert"
                className="mb-4 rounded-card border border-error bg-error-soft p-4 text-body text-error"
              >
                Không tải được thông tin tài khoản: {(accessError as Error).message}
              </p>
            )}
            {children}
          </div>
        </main>

        <div className="bg-primary px-4 py-5 lg:hidden">
          <Navigation
            role={role}
            pathname={pathname}
            familyName={familyName}
            isPending={isPending}
            id="mobile-navigation"
          />
          <Link
            href="/ui-kit"
            className="mt-4 inline-block text-caption text-cream-on-dark underline"
          >
            Hướng dẫn UI kit
          </Link>
        </div>
      </div>
    </div>
  );
}
