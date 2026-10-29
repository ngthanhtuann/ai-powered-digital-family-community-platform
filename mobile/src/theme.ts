// Token giao diện chép đúng giá trị từ frontend/tailwind.config.ts (design-system.md); không thêm màu mới.
export const colors = {
  primary: '#2C1810',
  secondary: '#C9A84C',
  success: '#2E7D32',
  error: '#C62828',
  info: '#1565C0',
  warning: '#BF4400',
  background: '#F5F0E8',
  surface: '#FFFFFF',
  surfaceMuted: '#F9F6F0',
  textPrimary: '#2C1810',
  textSecondary: '#7A6348',
  border: '#D4C4A8',
  goldHover: '#B79538',
  creamOnDark: '#F5E6C8',
  borderStrong: '#9C8560',
  successSoft: '#E8F5E9',
  errorSoft: '#FCE4EC',
  warningSoft: '#FFF3E0',
  infoSoft: '#E3F2FD',
} as const;

export const text = {
  pageTitle: { fontSize: 24, lineHeight: 32, fontWeight: '700' },
  sectionTitle: { fontSize: 18, lineHeight: 26, fontWeight: '600' },
  body: { fontSize: 14, lineHeight: 22 },
  label: { fontSize: 14, lineHeight: 20, fontWeight: '500' },
  caption: { fontSize: 12, lineHeight: 18 },
} as const;

export const radius = { control: 6, card: 12 } as const;

/** Nút cao 44, ô nhập cao 42 (design-system.md). */
export const size = { button: 44, input: 42 } as const;

/** Lề trang 16 ở cỡ 360. */
export const spacing = { page: 16, gap: 16 } as const;
