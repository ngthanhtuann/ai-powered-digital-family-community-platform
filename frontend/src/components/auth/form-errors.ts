import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from '@/lib/api';

export interface FormAlert {
  title: string;
  message: string;
}

/**
 * Đổi lỗi từ server thành lỗi dưới từng ô (theo error.details) và một thông báo chung
 * (Figma: Toast lỗi có mã lỗi ở dòng đầu, thông điệp ở dòng sau).
 */
export function applyServerError<T extends FieldValues>(
  error: unknown,
  fields: readonly Path<T>[],
  setError: UseFormSetError<T>,
): FormAlert {
  if (!(error instanceof ApiError)) {
    return { title: 'SERVER_001', message: 'Lỗi hệ thống, vui lòng thử lại sau' };
  }
  for (const detail of error.details) {
    if ((fields as readonly string[]).includes(detail.field)) {
      setError(detail.field as Path<T>, { type: 'server', message: detail.message });
    }
  }
  const message =
    error.code === 'RATE_001' && error.retryAfter
      ? `${error.message} (sau ${error.retryAfter} giây)`
      : error.message;
  return { title: error.code, message };
}
