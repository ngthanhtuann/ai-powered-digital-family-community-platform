import { LogoutButton } from '@/components/auth/LogoutButton';

// Membership NONE (hoặc bị từ chối) sau đăng nhập được chuyển đến đây.
// Màn "Nhập mã gia đình hoặc tạo gia đình" (UC-AUTH-06) hoàn thiện ở SCRUM-57.
export default function JoinFamilyPage() {
  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-3 px-6">
      <h1 className="text-page-title">Tham gia gia đình</h1>
      <p className="text-body text-text-secondary">
        Bạn chưa thuộc gia đình nào. Màn hình nhập mã gia đình hoặc tạo gia đình sẽ được hoàn thiện
        trong task SCRUM-57.
      </p>
      <LogoutButton />
    </main>
  );
}
