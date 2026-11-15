'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { Input } from '@/components/ui/TextField';
import { Modal } from '@/components/ui/Modal';
import { Table, type TableColumn } from '@/components/ui/Table';
import { Toast } from '@/components/ui/Toast';

const SAMPLE_ROWS = [
  { name: 'Nguyễn Văn An', role: 'Trưởng chi', status: 'Đang hoạt động' },
  { name: 'Trần Thị Bình', role: 'Thành viên', status: 'Đã xác minh' },
];

type SampleRow = (typeof SAMPLE_ROWS)[number];

const COLUMNS: TableColumn<SampleRow>[] = [
  { key: 'name', header: 'Họ và tên', render: (row) => row.name },
  { key: 'role', header: 'Vai trò', render: (row) => row.role },
  { key: 'status', header: 'Trạng thái', render: (row) => row.status },
];

export default function UiKitPage() {
  const [modalOpen, setModalOpen] = useState(false);
  const [toastVisible, setToastVisible] = useState(true);

  return (
    <div className="space-y-5">
      <div>
        <h2 className="text-page-title">Hướng dẫn UI kit</h2>
        <p className="mt-2 text-body text-text-secondary">
          Thành phần giao diện dùng chung theo design system FamilyConnect.
        </p>
      </div>

      <Card title="Nút" description="Primary, Secondary, Danger, disabled và loading.">
        <div className="flex flex-wrap gap-3">
          <Button fullWidth={false}>Primary</Button>
          <Button variant="secondary" fullWidth={false}>
            Secondary
          </Button>
          <Button variant="danger" fullWidth={false}>
            Danger
          </Button>
          <Button disabled fullWidth={false}>
            Disabled
          </Button>
          <Button loading fullWidth={false}>
            Đang xử lý
          </Button>
        </div>
      </Card>

      <Card title="Ô nhập" description="Nhãn và lỗi được liên kết với trường nhập.">
        <div className="grid gap-4 sm:grid-cols-2">
          <Input label="Họ và tên" placeholder="Nhập họ và tên" />
          <Input
            label="Email"
            type="email"
            placeholder="name@example.com"
            error="Email chưa đúng định dạng."
          />
        </div>
      </Card>

      <Card title="Bảng" description="Trên màn hình nhỏ, bảng có thể cuộn ngang.">
        <Table
          caption="Danh sách thành viên mẫu"
          columns={COLUMNS}
          rows={SAMPLE_ROWS}
          getRowKey={(row) => row.name}
        />
      </Card>

      <Card title="Modal" description="Hộp thoại có thể đóng bằng nút, phím Escape hoặc nhấn nền.">
        <Button fullWidth={false} onClick={() => setModalOpen(true)}>
          Mở hộp thoại
        </Button>
      </Card>

      <Card title="Toast" description="Thông báo thành công, lỗi, thông tin và cảnh báo.">
        <div className="grid gap-3">
          {toastVisible ? (
            <Toast
              variant="success"
              title="Đã lưu"
              message="Thay đổi của bạn đã được cập nhật."
              onClose={() => setToastVisible(false)}
            />
          ) : (
            <Button variant="secondary" fullWidth={false} onClick={() => setToastVisible(true)}>
              Hiện lại thông báo
            </Button>
          )}
          <Toast
            variant="error"
            title="Không thể lưu"
            message="Vui lòng kiểm tra thông tin và thử lại."
          />
          <Toast variant="info" title="Thông tin" message="Dữ liệu đã được làm mới." />
          <Toast
            variant="warning"
            title="Cần chú ý"
            message="Một số thông tin cần được xác nhận."
          />
        </div>
      </Card>

      <Modal
        open={modalOpen}
        title="Hộp thoại mẫu"
        description="Ví dụ component Modal dùng chung."
        onClose={() => setModalOpen(false)}
        actions={
          <Button variant="secondary" fullWidth={false} onClick={() => setModalOpen(false)}>
            Đóng
          </Button>
        }
      >
        <p className="text-body">
          Nội dung hộp thoại cuộn bên trong nếu vượt quá chiều cao màn hình.
        </p>
      </Modal>
    </div>
  );
}
