import { Card } from '@/components/ui/Card';

// PERM_002: thành viên chưa được xác minh sẽ được chuyển đến màn hình này.
export default function PendingApprovalPage() {
  return (
    <Card title="Tài khoản đang chờ duyệt" className="mx-auto max-w-2xl">
      <p className="text-body text-text-secondary">
        Quản trị viên của gia đình chưa xác minh tài khoản của bạn. Bạn sẽ dùng được chức năng này
        sau khi được duyệt.
      </p>
    </Card>
  );
}
