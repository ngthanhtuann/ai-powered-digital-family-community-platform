// Gọi API module Auth (docs/04-api/openapi/auth.yaml). Kiểu sinh từ OpenAPI, không viết tay.
import { api } from './api';
import type { components } from './generated/auth';
import { tokenStorage } from './token-storage';

type Schemas = components['schemas'];
export type LoginRequest = Schemas['LoginRequest'];
export type LoginData = Schemas['LoginData'];
export type RegisterRequest = Schemas['RegisterRequest'];
export type User = Schemas['User'];
export type Membership = Schemas['Membership'];
export type UpdateProfileRequest = Schemas['UpdateProfileRequest'];
export type ChangePasswordRequest = Schemas['ChangePasswordRequest'];

/** POST /auth/login: thành công thì lưu cặp token vào SecureStore (UC-AUTH-02). */
export async function login(body: LoginRequest): Promise<LoginData> {
  const data = await api.post<LoginData>('/auth/login', body);
  await tokenStorage.set(data.accessToken, data.refreshToken);
  return data;
}

/** POST /users: tạo tài khoản, không tự đăng nhập (UC-AUTH-01). */
export function register(body: RegisterRequest): Promise<User> {
  return api.post<User>('/users', body);
}

/** DELETE /auth/sessions/current rồi xóa token ở máy, kể cả khi gọi API lỗi (UC-AUTH-03). */
export async function logout(): Promise<void> {
  try {
    await api.delete('/auth/sessions/current');
  } catch {
    // Token hết hạn hoặc mất mạng: vẫn xóa token ở máy để đăng xuất.
  } finally {
    await tokenStorage.clear();
  }
}

export function getMyProfile(): Promise<User> {
  return api.get<User>('/users/me');
}

export function getMyMembership(): Promise<Membership> {
  return api.get<Membership>('/users/me/membership');
}

/** PATCH /users/me (UC-AUTH-05). */
export function updateMyProfile(body: UpdateProfileRequest): Promise<User> {
  return api.patch<User>('/users/me', body);
}

/** PUT /users/me/password: server thu hồi mọi refresh token, nên xóa token và đăng nhập lại (UC-AUTH-04). */
export async function changePassword(body: ChangePasswordRequest): Promise<void> {
  await api.put('/users/me/password', body);
  await tokenStorage.clear();
}
