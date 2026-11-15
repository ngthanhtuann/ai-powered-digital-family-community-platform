// Kiểu phản hồi theo docs/04-api/conventions.md (mục 2).
// Kiểu theo từng endpoint được sinh từ OpenAPI vào services/generated (npm run gen:api).

export interface ApiMeta {
  page: number; // bắt đầu từ 0
  size: number; // tối đa 100
  total: number;
}

export interface ApiSuccess<T> {
  success: true;
  data: T; // DELETE thành công: null
}

export interface ApiListSuccess<T> {
  success: true;
  data: T[];
  meta: ApiMeta;
}

export interface ApiErrorDetail {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  success: false;
  error: { code: string; message: string; details: ApiErrorDetail[] };
}

export interface PageParams {
  page?: number;
  size?: number;
  sort?: string; // ví dụ "createdAt,desc"
}
