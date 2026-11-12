import type { NavigatorScreenParams } from '@react-navigation/native';

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
  Login: { registered?: boolean } | undefined;
  Register: undefined;
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
  PersonPicker: { title: string; target: PersonPickTarget };
};

export type AppDrawerParamList = {
  Home: undefined;
  Feed: undefined;
  Events: undefined;
  /** `root`: người làm gốc cây, đến từ màn Chọn thành viên hoặc nút "Xem nhánh từ người này". */
  FamilyTree: { root?: PersonPick } | undefined;
  /** `a`, `b`: người thứ nhất, thứ hai vừa chọn. Mở từ màn chi tiết thì có `clearB` để bỏ người thứ hai cũ. */
  Relationship: { a?: PersonPick; b?: PersonPick; clearB?: boolean } | undefined;
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
};
