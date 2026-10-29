export type Role = 'MEMBER' | 'BRANCH_ADMIN' | 'SYSTEM_ADMIN';
export type AppRole = Role | 'UNVERIFIED';

export interface NavItem {
  label: string;
  href?: string;
  roles?: Role[];
  icon: string;
}

export interface NavSection {
  heading?: string;
  items: NavItem[];
  roles?: Role[];
}

const ADMIN_ROLES: Role[] = ['BRANCH_ADMIN', 'SYSTEM_ADMIN'];
const ALL_ROLES: Role[] = ['MEMBER', 'BRANCH_ADMIN', 'SYSTEM_ADMIN'];

export const NAV_SECTIONS: NavSection[] = [
  {
    items: [
      { label: 'Tổng quan', href: '/', icon: 'layout-dashboard', roles: ALL_ROLES },
      { label: 'Trợ lý AI', icon: 'sparkles', roles: ALL_ROLES },
    ],
  },
  {
    heading: 'Gia phả',
    items: [
      { label: 'Thành viên', icon: 'users', roles: ALL_ROLES },
      { label: 'Cây gia phả', icon: 'network', roles: ALL_ROLES },
      { label: 'Tra cứu xưng hô', icon: 'search', roles: ALL_ROLES },
    ],
  },
  {
    heading: 'Cộng đồng',
    items: [
      { label: 'Bảng tin', href: '/community', icon: 'message-square', roles: ALL_ROLES },
      { label: 'Sự kiện & Giỗ chạp', href: '/events', icon: 'calendar-days', roles: ALL_ROLES },
      { label: 'Danh bạ', href: '/directory', icon: 'contact-round', roles: ALL_ROLES },
    ],
  },
  {
    heading: 'Di sản',
    items: [
      { label: 'Lịch sử dòng họ', href: '/heritage', icon: 'book-open', roles: ALL_ROLES },
      { label: 'Thư viện ảnh', icon: 'image', roles: ALL_ROLES },
      { label: 'Kho lưu trữ số', icon: 'archive', roles: ALL_ROLES },
    ],
  },
  {
    heading: 'Quản lý',
    roles: ADMIN_ROLES,
    items: [
      { label: 'Duyệt yêu cầu tham gia', icon: 'user-check', roles: ADMIN_ROLES },
      { label: 'Thống kê và báo cáo', icon: 'chart-column', roles: ADMIN_ROLES },
    ],
  },
  {
    heading: 'Quản trị hệ thống',
    roles: ['SYSTEM_ADMIN'],
    items: [
      { label: 'Quản lý người dùng', icon: 'user-cog', roles: ['SYSTEM_ADMIN'] },
      { label: 'Kiểm duyệt nội dung', icon: 'shield-check', roles: ['SYSTEM_ADMIN'] },
      { label: 'Nhật ký kiểm toán', icon: 'history', roles: ['SYSTEM_ADMIN'] },
      { label: 'Sao lưu và khôi phục', icon: 'database', roles: ['SYSTEM_ADMIN'] },
      { label: 'Cấu hình hệ thống', icon: 'sliders-horizontal', roles: ['SYSTEM_ADMIN'] },
    ],
  },
  {
    heading: 'Tài khoản',
    items: [
      { label: 'Hồ sơ cá nhân', href: '/profile', icon: 'user-round' },
      { label: 'Đổi mật khẩu', href: '/profile#doi-mat-khau', icon: 'lock-keyhole' },
    ],
  },
];

export const FAMILY_NAV_ITEM: NavItem = {
  label: 'Gia đình',
  icon: 'house',
};

export function filterNavByRole(items: NavItem[], role: AppRole): NavItem[] {
  if (role === 'UNVERIFIED') return [];
  return items.filter((item) => !item.roles || item.roles.includes(role));
}

export function filterSectionsByRole(sections: NavSection[], role: AppRole): NavSection[] {
  if (role === 'UNVERIFIED') {
    return sections.filter((section) => section.heading === 'Tài khoản');
  }
  return sections
    .filter((section) => !section.roles || section.roles.includes(role))
    .map((section) => ({ ...section, items: filterNavByRole(section.items, role) }))
    .filter((section) => section.items.length > 0);
}
