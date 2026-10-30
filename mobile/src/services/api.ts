// Lớp gọi REST API cho mobile, cùng cách làm với frontend/src/lib/api.ts (mobile.md mục 4.3 đến 4.5).
import axios, {
  AxiosError,
  create as createAxios,
  isAxiosError,
  type AxiosRequestConfig,
} from 'axios';
import type {
  ApiErrorBody,
  ApiErrorDetail,
  ApiListSuccess,
  ApiSuccess,
  PageParams,
} from './api-types';
import type { components as AuthComponents } from './generated/auth';
import { tokenStorage } from './token-storage';

const API_BASE_URL = process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080';
const API_PREFIX = '/api/v1';

/** Lỗi chuẩn hóa theo conventions.md mục 2.2; `details` luôn là mảng. */
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

/**
 * Phiên đăng nhập (SessionContext) đăng ký để đổi màn hình khi API báo:
 * hết phiên (AUTH_002 không làm mới được), tài khoản bị khóa (AUTH_003), chưa được duyệt (PERM_002).
 */
export interface SessionHandlers {
  onSignedOut: (reason: 'expired' | 'locked') => void;
  onUnverified: () => void;
}
let handlers: SessionHandlers | null = null;
export function setSessionHandlers(next: SessionHandlers | null) {
  handlers = next;
}

const client = createAxios({
  baseURL: `${API_BASE_URL}${API_PREFIX}`,
  headers: { 'Content-Type': 'application/json' },
  timeout: 15000,
});

client.interceptors.request.use((config) => {
  const token = tokenStorage.getAccess();
  if (token) config.headers.set('Authorization', `Bearer ${token}`);
  return config;
});

type TokenData = AuthComponents['schemas']['TokenData'];
type RefreshRequest = AuthComponents['schemas']['RefreshRequest'];
type TokenResponse = AuthComponents['schemas']['TokenResponse'];

// Nhiều request cùng nhận AUTH_002 chỉ gọi refresh một lần, dùng chung kết quả.
let refreshing: Promise<TokenData> | null = null;

function refreshAccessToken(): Promise<TokenData> {
  if (!refreshing) {
    refreshing = (async () => {
      const refreshToken = await tokenStorage.getRefresh();
      if (!refreshToken) throw new Error('Không tìm thấy refresh token.');
      // axios trần: không đi qua interceptor, tránh lặp vô hạn.
      const res = await axios.post<TokenResponse>(`${API_BASE_URL}${API_PREFIX}/auth/refresh`, {
        refreshToken,
      } satisfies RefreshRequest);
      const tokens = res.data.data;
      await tokenStorage.set(tokens.accessToken, tokens.refreshToken);
      return tokens;
    })().finally(() => {
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
    return new ApiError(
      'NETWORK_ERROR',
      'Không kết nối được máy chủ. Kiểm tra mạng rồi thử lại.',
      0,
    );
  }
  return new ApiError('SERVER_001', 'Lỗi hệ thống, vui lòng thử lại sau.', error.response.status);
}

type RetriableConfig = AxiosRequestConfig & { _retried?: boolean };

/** Màn Đăng nhập, Đăng ký tự hiện lỗi xác thực của mình, không đẩy người dùng sang màn khác. */
const AUTH_FORM_URLS = ['/auth/login', '/users'];

client.interceptors.response.use(
  (res) => res,
  async (error: AxiosError<ApiErrorBody>) => {
    const apiError = toApiError(error);
    const original = error.config as RetriableConfig | undefined;
    const isAuthForm = AUTH_FORM_URLS.includes(original?.url ?? '');

    if (apiError.code === 'AUTH_002' && !isAuthForm) {
      if (original && !original._retried) {
        original._retried = true;
        try {
          const { accessToken } = await refreshAccessToken();
          original.headers = { ...original.headers, Authorization: `Bearer ${accessToken}` };
          return client.request(original);
        } catch (refreshError) {
          const locked =
            isAxiosError(refreshError) &&
            toApiError(refreshError as AxiosError<ApiErrorBody>).code === 'AUTH_003';
          await tokenStorage.clear();
          handlers?.onSignedOut(locked ? 'locked' : 'expired');
          return Promise.reject(apiError);
        }
      }
      await tokenStorage.clear();
      handlers?.onSignedOut('expired');
    } else if (apiError.code === 'AUTH_003' && !isAuthForm) {
      await tokenStorage.clear();
      handlers?.onSignedOut('locked');
    } else if (apiError.code === 'PERM_002') {
      handlers?.onUnverified();
    }
    // Các mã khác (PERM_001, VALID_001, RATE_001...) để màn hình tự hiện theo mobile.md mục 4.5.
    return Promise.reject(apiError);
  },
);

async function request<T>(config: AxiosRequestConfig): Promise<T> {
  const res = await client.request<ApiSuccess<T>>(config);
  return res.data.data;
}

export const api = {
  get: <T>(url: string, params?: object) => request<T>({ method: 'GET', url, params }),
  list: async <T>(url: string, params?: PageParams & Record<string, unknown>) => {
    const res = await client.request<ApiListSuccess<T>>({ method: 'GET', url, params });
    return { items: res.data.data, meta: res.data.meta };
  },
  post: <T>(url: string, data?: unknown) => request<T>({ method: 'POST', url, data }),
  put: <T>(url: string, data?: unknown) => request<T>({ method: 'PUT', url, data }),
  patch: <T>(url: string, data?: unknown) => request<T>({ method: 'PATCH', url, data }),
  delete: (url: string) => request<null>({ method: 'DELETE', url }),
};
