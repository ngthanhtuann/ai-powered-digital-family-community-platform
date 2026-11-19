import { redirect } from 'next/navigation';

/** Trang sửa nghề nghiệp, học vấn cũ: đã gộp vào "Chỉnh sửa hồ sơ" trên trang cá nhân (SCRUM-122). Link cũ chuyển sang đó. */
export default async function DirectoryMemberRedirect({ params }: { params: Promise<{ personId: string }> }) {
  const { personId } = await params;
  redirect(`/persons/${personId}`);
}
