// Quản trị hệ thống không thuộc gia đình nào: chọn gia đình đang xem, dùng chung cho mọi màn (như web, SCRUM-118).
import { useQuery } from '@tanstack/react-query';
import { dashboardApi } from '../services/admin';
import { useSession } from '../session/SessionContext';
import { Select } from './form-ui';

export function AdminFamilyPicker() {
  const { adminFamilyId, setAdminFamilyId } = useSession();
  const families = useQuery({ queryKey: ['dashboard-families'], queryFn: dashboardApi.families });
  return (
    <Select
      label="Gia đình đang xem"
      value={adminFamilyId ?? ''}
      disabled={!families.data}
      placeholder={
        families.isError ? 'Lỗi tải gia đình' : families.isPending ? 'Đang tải...' : 'Chọn gia đình'
      }
      onChange={(id) => setAdminFamilyId(id || null)}
      options={[
        { value: '', label: 'Chưa chọn' },
        ...(families.data ?? []).map((f) => ({ value: f.id ?? '', label: f.name ?? '' })),
      ]}
    />
  );
}
