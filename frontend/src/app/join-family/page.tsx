'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { TextField } from '@/components/ui/TextField';
import { getMyMembership } from '@/services/auth';
import { joinRequestsApi } from '@/services/join-requests';

const errorText = (error: unknown) => (error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.');
const textareaClass = 'w-full rounded-control border border-border-strong bg-surface-muted px-3 py-2 text-body '
  + 'text-text-primary outline-none focus-visible:outline-2 focus-visible:outline-info';

const joinSchema = z.object({
  code: z.string().trim().uuid('Mã gồm 36 ký tự, ví dụ 3f2a8c1e-0b7d-4e55-9a31-5d2c6b8e1a01'),
  message: z.string().trim().max(255, 'Tối đa 255 ký tự'),
});
type JoinValues = z.infer<typeof joinSchema>;

const createSchema = z.object({
  name: z.string().trim().min(1, 'Nhập tên gia đình').max(150, 'Tối đa 150 ký tự'),
});
type CreateValues = z.infer<typeof createSchema>;

/** UC-AUTH-06 bước 1, 2: nhập mã gia đình để xin tham gia, hoặc tự lập gia đình mới (UC-GEN-01). */
export default function JoinFamilyPage() {
  const router = useRouter();
  const membership = useQuery({ queryKey: ['my-membership'], queryFn: getMyMembership });
  const status = membership.data?.status;
  useEffect(() => {
    if (status === 'PENDING') router.replace('/pending-approval');
    if (status === 'APPROVED') router.replace('/');
  }, [status, router]);

  const joinForm = useForm<JoinValues>({ resolver: zodResolver(joinSchema), defaultValues: { code: '', message: '' } });
  const join = useMutation({
    mutationFn: (v: JoinValues) => joinRequestsApi.create({ code: v.code, message: v.message || null }),
    onSuccess: () => router.replace('/pending-approval'),
  });
  const createForm = useForm<CreateValues>({ resolver: zodResolver(createSchema), defaultValues: { name: '' } });
  const create = useMutation({
    mutationFn: (v: CreateValues) => joinRequestsApi.createFamily({ name: v.name }),
    // Tải lại để thanh điều hướng đọc vai trò trưởng họ mới.
    onSuccess: () => window.location.assign('/'),
  });

  return (
    <div className="mx-auto grid max-w-4xl gap-6">
      <header>
        <h1 className="text-page-title">Tham gia gia đình</h1>
        <p className="mt-1 text-body text-text-secondary">
          Bạn chưa thuộc gia đình nào. Nhập mã gia đình hoặc mã chi do người thân, trưởng họ hoặc trưởng chi gửi, hoặc lập gia đình mới.
        </p>
      </header>
      {membership.isLoading && <p className="text-body text-text-secondary">Đang kiểm tra tài khoản...</p>}
      {membership.isError && <p role="alert" className="text-body text-error">{errorText(membership.error)}</p>}
      {status === 'REJECTED' && (
        <p role="status" className="rounded-card border border-warning bg-warning-soft p-4 text-body">
          Yêu cầu trước của bạn bị từ chối{membership.data?.rejectReason ? `: ${membership.data.rejectReason}` : '.'} Bạn có thể gửi yêu cầu mới.
        </p>
      )}
      <div className="grid gap-6 md:grid-cols-2">
        <Card title="Nhập mã để tham gia" description="Mã chi đưa yêu cầu tới trưởng chi; mã gia đình tới trưởng họ.">
          <form className="grid gap-4" noValidate onSubmit={joinForm.handleSubmit((v) => join.mutate(v))}>
            <TextField label="Mã gia đình hoặc mã chi" placeholder="Dán mã được gửi cho bạn" autoComplete="off"
              error={joinForm.formState.errors.code?.message} {...joinForm.register('code')} />
            <label className="grid gap-1.5 text-label">Lời nhắn cho người duyệt (không bắt buộc)
              <textarea className={textareaClass} rows={3} maxLength={255}
                placeholder="Ví dụ: con gái ông Nguyễn Văn Bình, chi trưởng" {...joinForm.register('message')} />
              {joinForm.formState.errors.message && (
                <span className="text-caption text-error">{joinForm.formState.errors.message.message}</span>
              )}
            </label>
            {join.isError && <p role="alert" className="text-body text-error">{errorText(join.error)}</p>}
            <Button type="submit" loading={join.isPending}>Gửi yêu cầu</Button>
          </form>
        </Card>
        <Card title="Lập gia đình mới" description="Bạn sẽ là trưởng họ và có thể mời người thân tham gia.">
          <form className="grid gap-4" noValidate onSubmit={createForm.handleSubmit((v) => create.mutate(v))}>
            <TextField label="Tên gia đình" placeholder="Ví dụ: Dòng họ Nguyễn" maxLength={150}
              error={createForm.formState.errors.name?.message} {...createForm.register('name')} />
            {create.isError && <p role="alert" className="text-body text-error">{errorText(create.error)}</p>}
            <Button type="submit" variant="secondary" loading={create.isPending}>Lập gia đình</Button>
          </form>
        </Card>
      </div>
    </div>
  );
}
