import type { NavigatorScreenParams } from '@react-navigation/native';
import type { FamilyAccount, JoinRequest, AdminUser } from '../services/admin';
import type { SpouseRef } from '../services/genealogy-tree';

/** parent: thêm cha/mẹ cho người neo; child: thêm con; spouse: thêm vợ/chồng (như RelationModal của web). */
export type RelationMode = 'parent' | 'child' | 'spouse';

// Tên màn hình và tham số; thành viên khác thêm màn mới vào đây (mobile.md mục 3.3).

/** Màn Chọn thành viên trả người được chọn về màn gọi nó (SCRUM-90). */
export type PersonPickTarget = 'treeRoot' | 'relationA' | 'relationB';

/** Người vừa được chọn; `at` là mốc thời gian để màn nhận biết lần chọn mới, kể cả chọn lại cùng người. */
export interface PersonPick {
  id: string;
  name: string;
  at: number;
}

export type RootStackParamList = {
  Login: { registered?: boolean; passwordReset?: boolean } | undefined;
  Register: undefined;
  /** UC-AUTH-15: đặt lại mật khẩu bằng mã do trưởng họ hoặc quản trị cấp. */
  ForgotPassword: undefined;
  JoinFamily: undefined;
  PendingApproval: undefined;
  App: NavigatorScreenParams<AppDrawerParamList> | undefined;
  PostDetail: { postId: string };
  CreatePost: undefined;
  EventDetail: { eventId: string };
  Notifications: undefined;
  // Hồ sơ dùng được cả khi chưa xác minh (UC-AUTH-05): mở từ màn Nhập mã gia đình, Chờ duyệt.
  Profile: undefined;
  ChangePassword: undefined;
  /** Gia phả (SCRUM-90): màn của stack thay cho Modal để thanh trạng thái do app quản lý. */
  PersonDetail: { personId: string };
  /** Chính chủ sửa hồ sơ gia phả của mình (SCRUM-128). */
  EditProfile: { personId: string };
  PersonPicker: { title: string; target: PersonPickTarget };
  /** Di sản (SCRUM-92): đọc câu chuyện; xem ảnh hoặc tài liệu phóng to (`document` để báo khi là PDF). */
  StoryDetail: { storyId: string };
  PhotoViewer: { mediaId: string; title: string; document?: boolean; documentId?: string };
  // Quản lý gia phả (như các hộp thoại của web: RelationModal, PersonFormModal, DeathReportBox, PersonPanel).
  RelativeForm: {
    personId: string;
    mode: RelationMode;
    canFoundBranch?: boolean;
    spouses?: SpouseRef[];
  };
  PersonForm: { personId?: string };
  DeathForm: { personId: string };
  DeathReportForm: { personId: string };
  Divorce: { personId: string; personName: string; spouse: SpouseRef };
  ReplaceAccount: { personId: string };
  ResetCode: { userId: string; fullName: string };
  // Quản lý (trưởng họ, trưởng chi) và quản trị hệ thống.
  ApproveRequest: { request: JoinRequest; canPickBranch: boolean };
  RejectRequest: { request: JoinRequest };
  BranchForm: { familyId: string; branchId?: string };
  PickBranchHead: { familyId: string; branchId: string };
  AccountRole: { familyId: string; account: FamilyAccount };
  Invite: { familyId: string; personId: string };
  AdminLock: { user: AdminUser };
  AdminAssign: { user: AdminUser };
  // Cộng đồng, sự kiện, di sản: thêm, sửa.
  AnnouncementForm: { announcementId?: string };
  EventForm: { eventId?: string };
  DocumentForm: { documentId?: string };
  StoryForm: { storyId?: string };
  OutstandingForm: { memberId?: string };
};

export type AppDrawerParamList = {
  Home: undefined;
  Feed: undefined;
  Events: undefined;
  /** `root`: người làm gốc cây, đến từ màn Chọn thành viên hoặc nút "Xem nhánh từ người này". */
  FamilyTree: { root?: PersonPick } | undefined;
  /** `a`, `b`: người thứ nhất, thứ hai vừa chọn. Mở từ màn chi tiết thì có `clearB` để bỏ người thứ hai cũ. */
  Relationship: { a?: PersonPick; b?: PersonPick; clearB?: boolean } | undefined;
  Directory: undefined;
  Heritage: undefined;
  AiChat: undefined;
  AiSearch: undefined;
  AiSourceDetail: {
    sourceType: import('../services/ai').AiSourceType;
    sourceId: string;
    title?: string;
    snippet?: string;
  };
  Profile: undefined;
  ChangePassword: undefined;
  // Như menu web (design-system.md mục 5.2): Gia phả, Cộng đồng, Di sản, Quản lý, Quản trị hệ thống.
  Members: undefined;
  Announcements: undefined;
  OutstandingMembers: undefined;
  JoinRequests: undefined;
  FamilyRoles: undefined;
  Dashboard: undefined;
  Reports: undefined;
  AdminUsers: undefined;
  AdminModeration: undefined;
  AdminAudit: undefined;
  AdminBackup: undefined;
  AdminConfig: undefined;
};
