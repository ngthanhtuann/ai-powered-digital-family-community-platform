import { NextResponse, type NextRequest } from 'next/server';
import { SESSION_COOKIE } from '@/lib/token-storage';

// Bảo vệ route (SCRUM-36): chưa đăng nhập thì chuyển về /login, kèm ?next= để quay lại sau khi đăng nhập.
// Chỉ kiểm tra cookie đánh dấu phiên (xem lib/token-storage.ts); token hết hạn hay bị khóa
// do lib/api.ts xử lý khi backend trả 401 AUTH_002 hoặc 403 AUTH_003.
const PUBLIC_PATHS = ['/login', '/register'];

export function middleware(request: NextRequest) {
  const { pathname, search } = request.nextUrl;
  if (PUBLIC_PATHS.includes(pathname)) {
    return NextResponse.next();
  }
  if (request.cookies.get(SESSION_COOKIE)?.value) {
    return NextResponse.next();
  }
  const loginUrl = new URL('/login', request.url);
  if (pathname !== '/') {
    loginUrl.searchParams.set('next', pathname + search);
  }
  return NextResponse.redirect(loginUrl);
}

export const config = {
  // Bỏ qua tệp build của Next.js và mọi tệp tĩnh trong public/ (ảnh, icon).
  matcher: ['/((?!_next/static|_next/image|favicon.ico|.*\\.(?:svg|png|jpe?g|webp|gif|ico)$).*)'],
};
