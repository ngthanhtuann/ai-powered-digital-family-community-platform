'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { getMyMembership } from '@/services/auth';
import { joinRequestsApi } from '@/services/join-requests';

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.');

/** UC-AUTH-06 bước 5, 6: chờ duyệt, tự hỏi lại trạng thái; hủy yêu cầu; xem lý do bị từ chối. */
export default function PendingApprovalPage() {
  const router = useRouter();
  const cache = useQueryClient();
  const membership = useQuery({
    queryKey: ['my-membership'],
    queryFn: getMyMembership,
    refetchInterval: 30_000,
  });
  const data = membership.data;
  useEffect(() => {
    // Được duyệt: tải lại toàn trang để menu và quyền đọc theo trạng thái mới.
    if (data?.status === 'APPROVED') window.location.assign('/');
    if (data?.status === 'NONE') router.replace('/join-family');
  }, [data?.status, router]);
  const cancel = useMutation({
    mutationFn: () => joinRequestsApi.cancel(data!.joinRequestId!),
    onSuccess: async () => { await cache.invalidateQueries({ queryKey: ['my-membership'] }); },
  });

  return (
    <Card title={data?.status === 'REJECTED' ? 'Yêu cầu bị từ chối' : 'Tài khoản đang chờ duyệt'}
      className="mx-auto max-w-2xl">
      {membership.isLoading && <p className="text-body text-text-secondary">Đang kiểm tra trạng thái...</p>}
      {membership.isError && <p role="alert" className="text-body text-error">{errorText(membership.error)}</p>}
      {data?.status === 'PENDING' && (
        <div className="grid gap-4">
          <p className="text-body">
            Bạn đã xin tham gia <span className="font-semibold">{data.familyName ?? 'gia đình'}</span>
            {data.branchName ? ` (${data.branchName})` : ''}. {data.branchName ? 'Trưởng chi hoặc trưởng họ' : 'Trưởng họ'} sẽ duyệt yêu cầu; trang này tự cập nhật.
          </p>
          <p className="text-caption text-text-secondary">
            Trong lúc chờ, bạn vẫn xem và sửa được hồ sơ cá nhân.
          </p>
          {cancel.isError && <p role="alert" className="text-body text-error">{errorText(cancel.error)}</p>}
          <div className="flex flex-wrap gap-3">
            <Button fullWidth={false} variant="secondary" onClick={() => membership.refetch()}
              loading={membership.isFetching}>Kiểm tra lại</Button>
            <Button fullWidth={false} variant="danger" loading={cancel.isPending} disabled={!data.joinRequestId}
              onClick={() => { if (window.confirm('Hủy yêu cầu tham gia này?')) cancel.mutate(); }}>
              Hủy yêu cầu
            </Button>
          </div>
        </div>
      )}
      {data?.status === 'REJECTED' && (
        <div className="grid gap-4">
          <p className="text-body">
            {data.familyName ? `${data.familyName} đã từ chối yêu cầu của bạn` : 'Yêu cầu của bạn bị từ chối'}
            {data.rejectReason ? `: ${data.rejectReason}` : '.'}
          </p>
          <Link href="/join-family" className="text-body font-semibold text-info underline">Gửi yêu cầu mới</Link>
        </div>
      )}
    </Card>
  );
}
