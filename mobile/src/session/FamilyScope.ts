// Gia đình đang xem và quyền của người dùng, cùng cách tính với useHeritageAccess trên web.
// Quản trị hệ thống không thuộc gia đình nào: dùng gia đình đã chọn ở ô "Gia đình đang xem" (SCRUM-118).
import { useSession } from './SessionContext';

export interface FamilyScope {
  userId: string | null;
  familyId: string | null;
  familyName: string | null;
  /** Chi của tài khoản (trưởng chi: chi mình quản lý). */
  branchId: string | null;
  branchName: string | null;
  isSystemAdmin: boolean;
  /** Trưởng họ, trưởng chi hoặc quản trị hệ thống: quản lý gia phả trong phạm vi (BR-GEN-14). */
  isAdmin: boolean;
  /** BRANCH_ADMIN không gán chi là trưởng họ (BR-AUTH-22); quản trị hệ thống được làm như trưởng họ. */
  isFamilyHead: boolean;
  /** Gửi kèm `?familyId=` khi quản trị hệ thống tạo nội dung cho gia đình đang xem. */
  familyParam: string | undefined;
}

export function useFamilyScope(): FamilyScope {
  const { session, adminFamilyId } = useSession();
  if (session.status !== 'signedIn') {
    return {
      userId: null,
      familyId: null,
      familyName: null,
      branchId: null,
      branchName: null,
      isSystemAdmin: false,
      isAdmin: false,
      isFamilyHead: false,
      familyParam: undefined,
    };
  }
  const { user, membership } = session;
  const isSystemAdmin = user.roles.includes('SYSTEM_ADMIN');
  const own = membership.familyId ?? null;
  const familyId = own ?? (isSystemAdmin ? adminFamilyId : null);
  const branchAdmin = membership.role === 'BRANCH_ADMIN';
  return {
    userId: user.id,
    familyId,
    familyName: own ? (membership.familyName ?? null) : null,
    branchId: membership.branchId ?? null,
    branchName: membership.branchName ?? null,
    isSystemAdmin,
    isAdmin: isSystemAdmin || branchAdmin,
    isFamilyHead: isSystemAdmin || (branchAdmin && !membership.branchId),
    familyParam: !own && isSystemAdmin && familyId ? familyId : undefined,
  };
}
