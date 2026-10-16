// Khung menu ẩn theo vai trò (SCRUM-33). SCRUM-40 sẽ dùng để render sidebar.
// Vai trò đầy đủ theo SCRUM-43 (RBAC); hiện chỉ khai báo các vai trò đã biết.
export type Role = 'MEMBER' | 'BRANCH_ADMIN' | 'SYSTEM_ADMIN';

export interface NavItem {
  label: string;
  href: string;
  /** Không khai báo `roles` = mọi người dùng đã đăng nhập đều thấy. */
  roles?: Role[];
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Trang chủ', href: '/' },
  { label: 'Dashboard', href: '/dashboard', roles: ['BRANCH_ADMIN', 'SYSTEM_ADMIN'] },
];

export function filterNavByRole(items: NavItem[], role?: Role): NavItem[] {
  return items.filter((i) => !i.roles || (role !== undefined && i.roles.includes(role)));
}
