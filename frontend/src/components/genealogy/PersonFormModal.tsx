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
import { directoryApi, type EducationProfile, type ProfessionProfile } from '@/services/directory';

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
  // Nghề nghiệp, học vấn (UC-DIR-02) sửa chung trong form hồ sơ (SCRUM-122); để trống thì giữ như cũ.
  jobTitle: z.string().trim().max(150, 'Tối đa 150 ký tự'),
  company: z.string().trim().max(200, 'Tối đa 200 ký tự'),
  workLocation: z.string().trim().max(200, 'Tối đa 200 ký tự'),
  school: z.string().trim().max(200, 'Tối đa 200 ký tự'),
  degree: z.string().trim().max(100, 'Tối đa 100 ký tự'),
  graduationYear: z.string().refine((v) => v === '' || /^\d{4}$/.test(v), 'Năm gồm 4 chữ số'),
}).refine((v) => v.isDeceased || v.birthDate !== '', { path: ['birthDate'], message: 'Người còn sống phải có ngày sinh' });
type PersonValues = z.infer<typeof personSchema>;

/** career: truyền khi sửa hồ sơ ở trang cá nhân để sửa luôn nghề nghiệp, học vấn (null là chưa khai). */
export function PersonFormModal({ familyId, person, career, onClose }: {
  familyId: string; person?: Person; onClose: () => void;
  career?: { profession: ProfessionProfile | null; education: EducationProfile | null };
}) {
  const cache = useQueryClient();
  const { register, handleSubmit, watch, formState: { errors } } = useForm<PersonValues>({
    resolver: zodResolver(personSchema),
    defaultValues: {
      fullName: person?.fullName ?? '', gender: person?.gender ?? 'MALE', isDeceased: person?.isDeceased ?? false,
      birthDate: person?.birthDate ?? '', birthYear: person?.birthYear ? String(person.birthYear) : '',
      currentProvince: person?.currentProvince ?? '',
      birthplace: person?.birthplace ?? '', currentAddress: person?.currentAddress ?? '', note: person?.note ?? '',
      jobTitle: career?.profession?.jobTitle ?? '', company: career?.profession?.company ?? '',
      workLocation: career?.profession?.workLocation ?? '', school: career?.education?.school ?? '',
      degree: career?.education?.degree ?? '',
      graduationYear: career?.education?.graduationYear ? String(career.education.graduationYear) : '',
    },
  });
  const save = useMutation({
    mutationFn: async (v: PersonValues) => {
      const input = {
        ...(person ? toPersonInput(person) : { isDeceased: false }),
        fullName: v.fullName, gender: v.gender, isDeceased: v.isDeceased,
        birthDate: v.birthDate || null, birthYear: !v.birthDate && v.birthYear ? Number(v.birthYear) : null,
        currentProvince: v.currentProvince || null,
        birthplace: v.birthplace || null, currentAddress: v.currentAddress || null, note: v.note || null,
      };
      const saved = person?.id ? await genealogyApi.updatePerson(person.id, input) : await genealogyApi.createPerson(familyId, input);
      if (career && person?.id) {
        if (v.jobTitle) {
          await directoryApi.saveProfessionProfile(person.id,
            { jobTitle: v.jobTitle, company: v.company || null, workLocation: v.workLocation || null });
        }
        if (v.school) {
          await directoryApi.saveEducationProfile(person.id,
            { school: v.school, degree: v.degree || null, graduationYear: v.graduationYear ? Number(v.graduationYear) : null });
        }
      }
      return saved;
    },
    onSuccess: async () => {
      await cache.invalidateQueries({ queryKey: ['profession-profile'] });
      await cache.invalidateQueries({ queryKey: ['education-profile'] });
      await cache.invalidateQueries({ queryKey: ['members'] });
      await cache.invalidateQueries({ queryKey: ['person'] });
      onClose();
    },
  });
  return (
    <Modal open title={career ? 'Chỉnh sửa hồ sơ' : person ? 'Sửa thành viên' : 'Thêm thành viên'} onClose={onClose}>
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
        {career && (
          <>
            <fieldset className="grid gap-3 rounded-card border border-border p-4">
              <legend className="px-1 text-label">Nghề nghiệp</legend>
              <TextField label="Nghề nghiệp, chức danh" maxLength={150} error={errors.jobTitle?.message} {...register('jobTitle')} />
              <TextField label="Nơi làm việc" maxLength={200} error={errors.company?.message} {...register('company')} />
              <TextField label="Địa điểm làm việc" maxLength={200} error={errors.workLocation?.message} {...register('workLocation')} />
            </fieldset>
            <fieldset className="grid gap-3 rounded-card border border-border p-4">
              <legend className="px-1 text-label">Học vấn</legend>
              <TextField label="Trường" maxLength={200} error={errors.school?.message} {...register('school')} />
              <TextField label="Bằng cấp" maxLength={100} error={errors.degree?.message} {...register('degree')} />
              <TextField label="Năm tốt nghiệp" inputMode="numeric" maxLength={4} error={errors.graduationYear?.message}
                {...register('graduationYear')} />
            </fieldset>
          </>
        )}
        {save.isError && <p role="alert" className="text-body text-error">{errorText(save.error)}</p>}
        <Button type="submit" loading={save.isPending}>Lưu</Button>
      </form>
    </Modal>
  );
}
