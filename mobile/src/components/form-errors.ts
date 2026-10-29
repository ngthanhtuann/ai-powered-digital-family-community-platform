import type { FieldValues, Path, UseFormSetError } from 'react-hook-form';
import { ApiError } from '../services/api';
import type { NoticeMessage } from './ui';

/** Đổi lỗi server thành lỗi dưới từng ô (theo error.details) và một thông báo chung (mobile.md mục 4.5). */
export function applyServerError<T extends FieldValues>(
  error: unknown,
  fields: readonly Path<T>[],
  setError: UseFormSetError<T>,
): NoticeMessage {
  if (!(error instanceof ApiError)) {
    return { variant: 'error', title: 'SERVER_001', message: 'Lỗi hệ thống, vui lòng thử lại sau' };
  }
  for (const detail of error.details) {
    if ((fields as readonly string[]).includes(detail.field)) {
      setError(detail.field as Path<T>, { type: 'server', message: detail.message });
    }
  }
  const message =
    error.code === 'RATE_001' && error.retryAfter
      ? `${error.message} (thử lại sau ${error.retryAfter} giây)`
      : error.code === 'PERM_001'
        ? 'Bạn không đủ quyền thực hiện thao tác này'
        : error.message;
  return { variant: 'error', title: error.code, message };
}

/** BR-AUTH-03: đủ 16 tuổi. Ngày dạng YYYY-MM-DD. */
export function isAtLeast(dateOfBirth: string, years: number): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(dateOfBirth)) return false;
  const dob = new Date(`${dateOfBirth}T00:00:00`);
  if (Number.isNaN(dob.getTime())) return false;
  const limit = new Date(dob);
  limit.setFullYear(dob.getFullYear() + years);
  return limit <= new Date();
}

export const PHONE_PATTERN = /^(\+84|0)[0-9]{9,10}$/;
export const MIN_AGE = 16;
