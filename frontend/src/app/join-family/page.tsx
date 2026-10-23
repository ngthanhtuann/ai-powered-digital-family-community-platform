import { Card } from '@/components/ui/Card';

// Membership NONE (hoặc bị từ chối) sau đăng nhập được chuyển đến đây.
// Màn "Nhập mã gia đình hoặc tạo gia đình" (UC-AUTH-06) hoàn thiện ở SCRUM-57.
export default function JoinFamilyPage() {
  return (
    <Card title="Tham gia gia đình" className="mx-auto max-w-2xl">
      <p className="text-body text-text-secondary">
        Bạn chưa thuộc gia đình nào. Màn hình nhập mã gia đình hoặc tạo gia đình sẽ được hoàn thiện
        trong task SCRUM-57.
      </p>
    </Card>
  );
}
