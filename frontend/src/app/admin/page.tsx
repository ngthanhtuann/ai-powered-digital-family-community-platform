import { redirect } from 'next/navigation';

/** /admin cũ trỏ về trang đầu của khu quản trị (SCRUM-117). */
export default function AdminIndex() {
  redirect('/admin/nguoi-dung');
}
