// Phiên đăng nhập (mobile.md mục 4.2): người dùng và membership, nạp từ SecureStore khi mở ứng dụng.
// RootNavigator dựa vào trạng thái này để chọn nhánh màn hình (mobile.md mục 3.2).
import { useQueryClient } from '@tanstack/react-query';
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { NoticeMessage } from '../components/ui';
import { ApiError, setSessionHandlers } from '../services/api';
import {
  getMyMembership,
  getMyProfile,
  logout,
  type LoginData,
  type Membership,
  type User,
} from '../services/auth';
import { tokenStorage } from '../services/token-storage';

export type Session =
  | { status: 'loading' }
  | { status: 'error'; message: string }
  | { status: 'signedOut'; notice?: NoticeMessage }
  | { status: 'signedIn'; user: User; membership: Membership };

interface SessionApi {
  session: Session;
  signIn: (data: LoginData) => void;
  signOut: (notice?: NoticeMessage) => Promise<void>;
  /** Về màn Đăng nhập mà không gọi API (token đã bị thu hồi, ví dụ sau khi đổi mật khẩu). */
  endSession: (notice?: NoticeMessage) => void;
  /** Gọi lại GET /users/me/membership (màn Chờ duyệt, sau khi nhận PERM_002). */
  reloadMembership: () => Promise<void>;
  setUser: (user: User) => void;
  retry: () => void;
}

const SessionContext = createContext<SessionApi | null>(null);

const LOCKED: NoticeMessage = {
  variant: 'error',
  title: 'AUTH_003',
  message: 'Tài khoản đã bị khóa',
};
const EXPIRED: NoticeMessage = {
  variant: 'info',
  title: 'Phiên đăng nhập đã hết hạn',
  message: 'Mời bạn đăng nhập lại.',
};

/** Đọc token đã lưu rồi lấy hồ sơ và membership. Trả null nếu api.ts đã tự chuyển về Đăng nhập. */
async function resolveSession(): Promise<Session | null> {
  if (!(await tokenStorage.load())) return { status: 'signedOut' };
  try {
    const [user, membership] = await Promise.all([getMyProfile(), getMyMembership()]);
    return { status: 'signedIn', user, membership };
  } catch (error) {
    // AUTH_002, AUTH_003 đã được api.ts xử lý; còn lại (mất mạng, 500) cho thử lại.
    if (error instanceof ApiError && error.code.startsWith('AUTH_')) return null;
    return { status: 'error', message: (error as Error).message };
  }
}

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [session, setSession] = useState<Session>({ status: 'loading' });

  const toSignedOut = useCallback(
    (notice?: NoticeMessage) => {
      queryClient.clear();
      setSession({ status: 'signedOut', notice });
    },
    [queryClient],
  );

  const load = useCallback(() => {
    resolveSession().then((next) => next && setSession(next));
  }, []);

  const reloadMembership = useCallback(async () => {
    const membership = await getMyMembership();
    setSession((s) => (s.status === 'signedIn' ? { ...s, membership } : s));
  }, []);

  useEffect(() => {
    setSessionHandlers({
      onSignedOut: (reason) => toSignedOut(reason === 'locked' ? LOCKED : EXPIRED),
      onUnverified: () => {
        reloadMembership().catch(() => undefined);
      },
    });
    load();
    return () => setSessionHandlers(null);
  }, [load, reloadMembership, toSignedOut]);

  const value = useMemo<SessionApi>(
    () => ({
      session,
      signIn: (data) =>
        setSession({ status: 'signedIn', user: data.user, membership: data.membership }),
      signOut: async (notice) => {
        await logout();
        toSignedOut(notice);
      },
      endSession: toSignedOut,
      reloadMembership,
      setUser: (user) => setSession((s) => (s.status === 'signedIn' ? { ...s, user } : s)),
      retry: () => {
        setSession({ status: 'loading' });
        load();
      },
    }),
    [session, toSignedOut, reloadMembership, load],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): SessionApi {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSession phải nằm trong SessionProvider');
  return ctx;
}

/** Màn hình sau đăng nhập (UC-AUTH-02 bước 6): SYSTEM_ADMIN và APPROVED vào ứng dụng. */
export function branchOf(user: User, membership: Membership): 'app' | 'pending' | 'join' {
  if (user.roles.includes('SYSTEM_ADMIN') || membership.status === 'APPROVED') return 'app';
  if (membership.status === 'PENDING') return 'pending';
  return 'join';
}
