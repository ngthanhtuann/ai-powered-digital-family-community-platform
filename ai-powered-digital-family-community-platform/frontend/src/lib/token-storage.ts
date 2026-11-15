// Lưu token theo auth.md mục 4.2: Web dùng localStorage (access token và refresh token).
// Middleware Next.js chạy phía server nên không đọc được localStorage; vì vậy khi đăng nhập
// đặt thêm cookie đánh dấu "đã có phiên" (SESSION_COOKIE). Cookie này KHÔNG chứa token,
// chỉ để middleware chuyển người chưa đăng nhập về /login; quyền thật vẫn do backend kiểm tra.
const ACCESS_KEY = 'fc.accessToken';
const REFRESH_KEY = 'fc.refreshToken';

export const SESSION_COOKIE = 'fc_session';
/** Bằng thời hạn mặc định của refresh token (7 ngày). */
const SESSION_COOKIE_MAX_AGE = 7 * 24 * 60 * 60;

const isBrowser = () => typeof window !== 'undefined';

function setSessionCookie(maxAge: number) {
  const secure = window.location.protocol === 'https:' ? '; Secure' : '';
  document.cookie = `${SESSION_COOKIE}=1; Path=/; Max-Age=${maxAge}; SameSite=Lax${secure}`;
}

export const tokenStorage = {
  getAccess: () => (isBrowser() ? localStorage.getItem(ACCESS_KEY) : null),
  getRefresh: () => (isBrowser() ? localStorage.getItem(REFRESH_KEY) : null),
  set(access: string, refresh: string) {
    if (!isBrowser()) return;
    localStorage.setItem(ACCESS_KEY, access);
    localStorage.setItem(REFRESH_KEY, refresh);
    setSessionCookie(SESSION_COOKIE_MAX_AGE);
  },
  clear() {
    if (!isBrowser()) return;
    localStorage.removeItem(ACCESS_KEY);
    localStorage.removeItem(REFRESH_KEY);
    setSessionCookie(0);
  },
};
