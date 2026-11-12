// Tên màn hình và tham số; thành viên khác thêm màn mới vào đây (mobile.md mục 3.3).
export type RootStackParamList = {
  Login: { registered?: boolean } | undefined;
  Register: undefined;
  JoinFamily: undefined;
  PendingApproval: undefined;
  App: undefined;
  PostDetail: { postId: string };
  CreatePost: undefined;
  EventDetail: { eventId: string };
  Notifications: undefined;
  // Hồ sơ dùng được cả khi chưa xác minh (UC-AUTH-05): mở từ màn Nhập mã gia đình, Chờ duyệt.
  Profile: undefined;
  ChangePassword: undefined;
};

export type AppDrawerParamList = {
  Home: undefined;
  Feed: undefined;
  Events: undefined;
  FamilyTree: undefined;
  /** Mở từ thẻ chi tiết trên cây thì điền sẵn người thứ nhất. */
  Relationship: { personAId?: string; personAName?: string } | undefined;
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
