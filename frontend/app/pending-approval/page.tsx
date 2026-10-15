// PERM_002: thành viên chưa được xác minh sẽ được chuyển đến màn hình này.
export default function PendingApprovalPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-3 px-6">
      <h1 className="text-2xl font-semibold">Tài khoản đang chờ duyệt</h1>
      <p className="text-muted">
        Quản trị viên của gia đình chưa xác minh tài khoản của bạn. Bạn sẽ dùng được chức năng này
        sau khi được duyệt.
      </p>
    </main>
  );
}
