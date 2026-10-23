import axios, { AxiosError, type AxiosRequestConfig } from 'axios';
import type {
  ApiErrorBody,
  ApiErrorDetail,
  ApiListSuccess,
  ApiSuccess,
  PageParams,
} from './api-types';
import type { components as AuthComponents } from '@/services/generated/auth';
import { tokenStorage } from './token-storage';

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
const API_PREFIX = '/api/v1';

export const API_EVENTS = { forbidden: 'api:forbidden' } as const;

/** Lỗi chuẩn hoá theo conventions.md mục 2.2; `details` luôn là mảng. */
export class ApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public status: number,
    public details: ApiErrorDetail[] = [],
    /** Số giây phải chờ khi bị 429 RATE_001 (header Retry-After). */
    public retryAfter?: number,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

const client = axios.create({
  baseURL: `${API_BASE_URL}${API_PREFIX}`,
  headers: { 'Content-Type': 'application/json' },
});

// Tự gắn Authorization: Bearer <access_token>
client.interceptors.request.use((config) => {
  const token = tokenStorage.getAccess();
  if (token) config.headers.set('Authorization', `Bearer ${token}`);
  return config;
});

type RefreshRequest = AuthComponents['schemas']['RefreshRequest'];
type RefreshResponse = AuthComponents['schemas']['TokenResponse'];

// Refresh dùng 1 request chung để nhiều request 401 không gọi refresh song song.
let refreshing: Promise<AuthComponents['schemas']['TokenData']> | null = null;

function refreshAccessToken(): Promise<AuthComponents['schemas']['TokenData']> {
  if (!refreshing) {
    const refreshToken = tokenStorage.getRefresh();
    // Dùng axios trần để không đi qua interceptor (tránh lặp vô hạn).
    if (!refreshToken) {
      return Promise.reject(new Error('Không tìm thấy refresh token.'));
    }
    refreshing = axios
      .post<RefreshResponse>(`${API_BASE_URL}${API_PREFIX}/auth/refresh`, {
        refreshToken,
      } satisfies RefreshRequest)
      .then((res) => {
        if (res.data.success !== true) {
          throw new Error('Backend không xác nhận refresh token thành công.');
        }
        const tokens = res.data.data;
        tokenStorage.set(tokens.accessToken, tokens.refreshToken);
        return tokens;
      })
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

function toApiError(error: AxiosError<ApiErrorBody>): ApiError {
  const body = error.response?.data;
  if (body && body.success === false && body.error) {
    const { code, message, details } = body.error;
    const retryAfter = Number(error.response!.headers['retry-after']);
    return new ApiError(
      code,
      message,
      error.response!.status,
      Array.isArray(details) ? details : [],
      Number.isFinite(retryAfter) && retryAfter > 0 ? retryAfter : undefined,
    );
  }
  if (!error.response) {
    return new ApiError('NETWORK_ERROR', 'Không kết nối được máy chủ.', 0);
  }
  return new ApiError('SERVER_001', 'Lỗi hệ thống.', error.response.status);
}

type RetriableConfig = AxiosRequestConfig & { _retried?: boolean };

client.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<ApiErrorBody>) => {
    const apiError = toApiError(error);
    const original = error.config as RetriableConfig | undefined;

    switch (apiError.code) {
      case 'AUTH_002': // token hết hạn/không hợp lệ -> refresh 1 lần rồi gọi lại
        if (original && !original._retried && tokenStorage.getRefresh()) {
          original._retried = true;
          try {
            const token = (await refreshAccessToken()).accessToken;
            original.headers = { ...original.headers, Authorization: `Bearer ${token}` };
            return client.request(original);
          } catch (refreshError) {
            // Tài khoản bị khóa lúc làm mới token (403 AUTH_003): báo bị khóa thay vì về đăng nhập trống.
            if (axios.isAxiosError(refreshError)) {
              const refreshApiError = toApiError(refreshError as AxiosError<ApiErrorBody>);
              if (refreshApiError.code === 'AUTH_003') {
                tokenStorage.clear();
                if (typeof window !== 'undefined') window.location.assign('/login?reason=locked');
                return Promise.reject(refreshApiError);
              }
            }
            // refresh thất bại vì lý do khác: rơi xuống đăng xuất
          }
        }
        tokenStorage.clear();
        if (typeof window !== 'undefined') window.location.assign('/login');
        break;
      case 'AUTH_003': // tài khoản bị khóa -> xóa token, về đăng nhập và báo bị khóa
        if (original?.url === '/auth/login') break; // trang đăng nhập tự hiện lỗi
        tokenStorage.clear();
        if (typeof window !== 'undefined') window.location.assign('/login?reason=locked');
        break;
      case 'PERM_002': // chưa xác minh -> màn hình chờ duyệt
        if (typeof window !== 'undefined') window.location.assign('/pending-approval');
        break;
      case 'PERM_001': // không đủ quyền -> thông báo
        if (typeof window !== 'undefined') {
          window.dispatchEvent(new CustomEvent(API_EVENTS.forbidden, { detail: apiError.message }));
        }
        break;
    }
    return Promise.reject(apiError);
  },
);

/** Gọi API trả về một đối tượng: trả `data`. DELETE thành công trả `null` (HTTP 200). */
export async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await client.request<ApiSuccess<T>>(config);
  return res.data.data;
}

/** Gọi API danh sách: trả `data` (mảng) và `meta`. */
export async function requestList<T>(
  config: AxiosRequestConfig,
): Promise<{ items: T[]; meta: ApiListSuccess<T>['meta'] }> {
  const res = await client.request<ApiListSuccess<T>>(config);
  return { items: res.data.data, meta: res.data.meta };
}

export const api = {
  get: <T>(url: string, params?: object) => request<T>({ method: 'GET', url, params }),
  list: <T>(url: string, params?: PageParams & Record<string, unknown>) =>
    requestList<T>({ method: 'GET', url, params }),
  post: <T>(url: string, data?: unknown) => request<T>({ method: 'POST', url, data }),
  put: <T>(url: string, data?: unknown) => request<T>({ method: 'PUT', url, data }),
  patch: <T>(url: string, data?: unknown) => request<T>({ method: 'PATCH', url, data }),
  delete: (url: string) => request<null>({ method: 'DELETE', url }),
};

/** Gọi thử /actuator/health (nằm ngoài /api/v1, không bọc envelope). */
export async function checkHealth(): Promise<{ status: string }> {
  const res = await axios.get<{ status: string }>(`${API_BASE_URL}/actuator/health`, {
    timeout: 5000,
  });
  return res.data;
}
