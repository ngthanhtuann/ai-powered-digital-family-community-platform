'use client';

import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { issuePasswordResetCode } from '@/services/auth';

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.');

/** UC-AUTH-15: trưởng họ hoặc quản trị cấp mã đặt lại mật khẩu; mã chỉ hiện một lần trong hộp thoại này. */
export function ResetCodeModal({ userId, fullName, onClose }: { userId: string; fullName: string; onClose: () => void }) {
  const [copied, setCopied] = useState(false);
  const issue = useMutation({ mutationFn: () => issuePasswordResetCode(userId) });
  const data = issue.data;
  return (
    <Modal open title={`Đặt lại mật khẩu cho ${fullName}`}
      description="Dùng khi người thân quên mật khẩu. Mã dùng một lần, hết hạn sau 30 phút; cấp mã mới thì mã cũ hết hiệu lực."
      onClose={onClose}>
      <div className="grid gap-4">
        {!data && (
          <Button loading={issue.isPending} onClick={() => issue.mutate()}>Tạo mã</Button>
        )}
        {issue.isError && <p role="alert" className="text-body text-error">{errorText(issue.error)}</p>}
        {data && (
          <>
            <p className="rounded-control bg-surface-muted p-4 text-center font-mono text-page-title tracking-widest" aria-label="Mã đặt lại mật khẩu">
              {data.code}
            </p>
            <p className="text-body text-text-secondary">
              Hết hạn lúc {new Date(data.expiresAt).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}.
              Mã chỉ hiện một lần: gửi riêng cho {fullName}. Người đó vào màn đăng nhập, chọn &quot;Quên mật khẩu?&quot;,
              nhập email, mã và mật khẩu mới.
            </p>
            <Button variant="secondary" onClick={() => {
              void navigator.clipboard?.writeText(data.code).then(() => setCopied(true), () => setCopied(false));
            }}>{copied ? 'Đã sao chép' : 'Sao chép mã'}</Button>
          </>
        )}
      </div>
    </Modal>
  );
}
