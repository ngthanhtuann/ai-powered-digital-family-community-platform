'use client';

import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { Button } from '@/components/ui/Button';
import { Modal } from '@/components/ui/Modal';
import { TextField } from '@/components/ui/TextField';
import { errorText, selectClass } from '@/components/genealogy/RelationModal';
import { genealogyApi, toPersonInput, type Person } from '@/services/genealogy';

// UC-GEN-03: người còn sống phải có ngày sinh đầy đủ; người đã mất có thể chỉ biết năm sinh (BR-GEN-17).
const personSchema = z.object({
  fullName: z.string().trim().min(1, 'Họ tên không được để trống').max(150, 'Tối đa 150 ký tự'),
  gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
  isDeceased: z.boolean(),
  birthDate: z.string(),
  birthYear: z.string().refine((v) => v === '' || /^\d{4}$/.test(v), 'Năm gồm 4 chữ số'),
  currentProvince: z.string().trim().max(100, 'Tối đa 100 ký tự'),
  birthplace: z.string().trim().max(255, 'Tối đa 255 ký tự'),
  currentAddress: z.string().trim().max(255, 'Tối đa 255 ký tự'),
  note: z.string().trim().max(2000, 'Tối đa 2000 ký tự'),
}).refine((v) => v.isDeceased || v.birthDate !== '', { path: ['birthDate'], message: 'Người còn sống phải có ngày sinh' });
type PersonValues = z.infer<typeof personSchema>;

export function PersonFormModal({ familyId, person, onClose }: { familyId: string; person?: Person; onClose: () => void }) {
  const cache = useQueryClient();
  const { register, handleSubmit, watch, formState: { errors } } = useForm<PersonValues>({
    resolver: zodResolver(personSchema),
    defaultValues: {
      fullName: person?.fullName ?? '', gender: person?.gender ?? 'MALE', isDeceased: person?.isDeceased ?? false,
      birthDate: person?.birthDate ?? '', birthYear: person?.birthYear ? String(person.birthYear) : '',
      currentProvince: person?.currentProvince ?? '',
      birthplace: person?.birthplace ?? '', currentAddress: person?.currentAddress ?? '', note: person?.note ?? '',
    },
  });
  const save = useMutation({
    mutationFn: (v: PersonValues) => {
      const input = {
        ...(person ? toPersonInput(person) : { isDeceased: false }),
        fullName: v.fullName, gender: v.gender, isDeceased: v.isDeceased,
        birthDate: v.birthDate || null, birthYear: !v.birthDate && v.birthYear ? Number(v.birthYear) : null,
        currentProvince: v.currentProvince || null,
        birthplace: v.birthplace || null, currentAddress: v.currentAddress || null, note: v.note || null,
      };
      return person?.id ? genealogyApi.updatePerson(person.id, input) : genealogyApi.createPerson(familyId, input);
    },
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['members'] });
      await cache.invalidateQueries({ queryKey: ['person'] });
      onClose();
    },
  });
  return (
    <Modal open title={person ? 'Sửa thành viên' : 'Thêm thành viên'} onClose={onClose}>
      <form className="grid gap-4 p-5" noValidate onSubmit={handleSubmit((v) => save.mutate(v))}>
        <TextField label="Họ tên" maxLength={150} error={errors.fullName?.message} {...register('fullName')} />
        <label className="grid gap-1 text-label">Giới tính
          <select className={selectClass} {...register('gender')}>
            <option value="MALE">Nam</option><option value="FEMALE">Nữ</option><option value="OTHER">Khác</option>
          </select>
        </label>
        <label className="flex min-h-11 items-center gap-2 text-body">
          <input type="checkbox" className="h-5 w-5" {...register('isDeceased')} /> Đã mất
        </label>
        <TextField label="Ngày sinh" type="date" error={errors.birthDate?.message} {...register('birthDate')} />
        {watch('isDeceased') && !watch('birthDate') && (
          <TextField label="Năm sinh (nếu không rõ ngày)" inputMode="numeric" maxLength={4}
            error={errors.birthYear?.message} {...register('birthYear')} />
        )}
        <TextField label="Tỉnh/thành đang sống" maxLength={100} error={errors.currentProvince?.message}
          {...register('currentProvince')} />
        <TextField label="Quê quán" maxLength={255} error={errors.birthplace?.message} {...register('birthplace')} />
        <TextField label="Địa chỉ" maxLength={255} error={errors.currentAddress?.message} {...register('currentAddress')} />
        <label className="grid gap-1 text-label">Giới thiệu
          <textarea className={selectClass + ' min-h-24 py-2'} maxLength={2000} {...register('note')} />
        </label>
        {errors.note && <p role="alert" className="text-caption text-error">{errors.note.message}</p>}
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending}>Lưu</Button>
      </form>
    </Modal>
  );
}
