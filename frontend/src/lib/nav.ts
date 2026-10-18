// Khung menu ẩn theo vai trò (SCRUM-33). SCRUM-40 sẽ dùng để render sidebar.
// Tên mục theo design-system.md mục 5.2; danh sách đầy đủ bổ sung ở SCRUM-40.
// Vai trò đầy đủ theo SCRUM-43 (RBAC); hiện chỉ khai báo các vai trò đã biết.
export type Role = 'MEMBER' | 'BRANCH_ADMIN' | 'SYSTEM_ADMIN';

export interface NavItem {
  label: string;
  href: string;
  /** Không khai báo `roles` = mọi người dùng đã đăng nhập đều thấy. */
  roles?: Role[];
}

export const NAV_ITEMS: NavItem[] = [
  { label: 'Tổng quan', href: '/' },
  {
    label: 'Thống kê và báo cáo',
    href: '/dashboard',
    roles: ['BRANCH_ADMIN', 'SYSTEM_ADMIN'],
  },
];

export function filterNavByRole(items: NavItem[], role?: Role): NavItem[] {
  return items.filter((i) => !i.roles || (role !== undefined && i.roles.includes(role)));
}
