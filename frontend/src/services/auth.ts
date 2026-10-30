// Gọi API module Auth (docs/04-api/openapi/auth.yaml). Kiểu sinh từ OpenAPI, không viết tay.
import { api, ApiError } from '@/lib/api';
import { tokenStorage } from '@/lib/token-storage';
import type { components } from './generated/auth';

type Schemas = components['schemas'];
export type LoginRequest = Schemas['LoginRequest'];
export type LoginData = Schemas['LoginData'];
export type RegisterRequest = Schemas['RegisterRequest'];
export type User = Schemas['User'];
export type Membership = Schemas['Membership'];
export type UpdateProfileRequest = Schemas['UpdateProfileRequest'];
export type ChangePasswordRequest = Schemas['ChangePasswordRequest'];

/** POST /auth/login: thành công thì lưu cặp token. */
export async function login(body: LoginRequest): Promise<LoginData> {
  const data = await api.post<LoginData>('/auth/login', body);
  tokenStorage.set(data.accessToken, data.refreshToken);
  return data;
}

/** POST /users: tạo tài khoản, không tự đăng nhập (UC-AUTH-01). */
export function register(body: RegisterRequest): Promise<User> {
  return api.post<User>('/users', body);
}

/** DELETE /auth/sessions/current rồi xóa token đã lưu và về màn Đăng nhập (UC-AUTH-03). */
export async function logout(): Promise<void> {
  try {
    await api.delete('/auth/sessions/current');
  } catch (error) {
    // Tài khoản bị khóa: lib/api.ts đã xóa token và chuyển tới /login?reason=locked, không đè lên.
    if (error instanceof ApiError && error.code === 'AUTH_003') return;
    // Lỗi khác (token hết hạn, mất mạng): vẫn xóa token ở máy để đăng xuất.
  }
  tokenStorage.clear();
  window.location.assign('/login');
}

/** GET /users/me/membership. */
export function getMyMembership(): Promise<Membership> {
  return api.get<Membership>('/users/me/membership');
}

/** GET /users/me. */
export function getMyProfile(): Promise<User> {
  return api.get<User>('/users/me');
}

/** PATCH /users/me: sửa họ tên, số điện thoại, ngày sinh (UC-AUTH-05). */
export function updateMyProfile(body: UpdateProfileRequest): Promise<User> {
  return api.patch<User>('/users/me', body);
}

/** PUT /users/me/password: server thu hồi mọi refresh token, nên gọi xong phải đăng nhập lại (UC-AUTH-04). */
export async function changePassword(body: ChangePasswordRequest): Promise<void> {
  await api.put('/users/me/password', body);
  tokenStorage.clear();
}

/**
 * Chọn màn hình sau đăng nhập (Figma "Đăng nhập — trạng thái", auth.md UC-AUTH-02 bước 6):
 * SYSTEM_ADMIN và APPROVED vào ứng dụng; PENDING vào màn chờ duyệt; còn lại vào màn xin tham gia.
 * `next` là trang người dùng định mở trước khi bị chuyển về /login.
 */
export function routeAfterLogin(data: LoginData, next: string | null): string {
  const safeNext = next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
  if (data.user.roles.includes('SYSTEM_ADMIN') || data.membership.status === 'APPROVED') {
    return safeNext;
  }
  if (data.membership.status === 'PENDING') {
    return '/pending-approval';
  }
  return '/join-family';
}
