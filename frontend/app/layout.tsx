import type { Metadata } from 'next';
import { Inter } from 'next/font/google';
import './globals.css';
import { Providers } from './providers';

const inter = Inter({
  subsets: ['latin', 'vietnamese'],
  variable: '--font-inter',
  display: 'swap',
});

export const metadata: Metadata = {
  title: 'FamilyConnect',
  description: 'Nền tảng gia đình số tích hợp AI',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="vi" className={inter.variable}>
      <body className="font-sans">
        <Providers>
          {/* Khung rỗng: SCRUM-40 (3-06) sẽ thêm header/sidebar. Menu lọc theo vai trò: lib/nav.ts */}
          {children}
        </Providers>
      </body>
    </html>
  );
}
