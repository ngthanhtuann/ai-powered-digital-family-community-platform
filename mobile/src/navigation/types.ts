// Tên màn hình và tham số; thành viên khác thêm màn mới vào đây (mobile.md mục 3.3).
export type RootStackParamList = {
  Login: { registered?: boolean } | undefined;
  Register: undefined;
  JoinFamily: undefined;
  PendingApproval: undefined;
  App: undefined;
  // Hồ sơ dùng được cả khi chưa xác minh (UC-AUTH-05): mở từ màn Nhập mã gia đình, Chờ duyệt.
  Profile: undefined;
  ChangePassword: undefined;
};

export type AppDrawerParamList = {
  Home: undefined;
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
