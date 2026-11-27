// Ô nhập dùng chung cho các màn quản lý (thay select, checkbox, textarea, phân trang của web); không thêm thư viện mới.
import { useState, type ReactNode } from 'react';
import { Alert, FlatList, Modal, Pressable, Text, TextInput, View } from 'react-native';
import { colors, radius, size, text } from '../theme';
import { Button } from './ui';

export interface Option<T extends string> {
  value: T;
  label: string;
}

/** Ô chọn một giá trị: bấm mở danh sách (giống select của web, cao 42). */
export function Select<T extends string>({
  label,
  value,
  options,
  onChange,
  placeholder = 'Chọn',
  disabled,
}: {
  label: string;
  value: T | '' | null | undefined;
  options: Option<T>[];
  onChange: (value: T) => void;
  placeholder?: string;
  disabled?: boolean;
}) {
  const [open, setOpen] = useState(false);
  const current = options.find((o) => o.value === value);
  return (
    <View style={{ gap: 6 }}>
      <Text style={[text.label, { color: colors.textPrimary }]}>{label}</Text>
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`${label}: ${current?.label ?? placeholder}`}
        accessibilityState={{ disabled }}
        disabled={disabled}
        onPress={() => setOpen(true)}
        style={{
          minHeight: size.input,
          borderWidth: 1,
          borderColor: colors.borderStrong,
          borderRadius: radius.control,
          backgroundColor: disabled ? colors.surfaceMuted : colors.surface,
          paddingHorizontal: 12,
          flexDirection: 'row',
          alignItems: 'center',
        }}
      >
        <Text
          style={[
            text.body,
            { flex: 1, color: current ? colors.textPrimary : colors.textSecondary },
          ]}
        >
          {current?.label ?? placeholder}
        </Text>
        <Text style={{ color: colors.textSecondary }}>▾</Text>
      </Pressable>
      <Modal visible={open} transparent animationType="fade" onRequestClose={() => setOpen(false)}>
        <Pressable
          accessibilityLabel="Đóng danh sách"
          onPress={() => setOpen(false)}
          style={{
            flex: 1,
            backgroundColor: 'rgba(44,24,16,0.4)',
            justifyContent: 'center',
            padding: 24,
          }}
        >
          <View
            style={{
              backgroundColor: colors.surface,
              borderRadius: radius.card,
              maxHeight: '75%',
              paddingVertical: 8,
            }}
          >
            <Text style={[text.sectionTitle, { color: colors.textPrimary, padding: 16 }]}>
              {label}
            </Text>
            <FlatList
              data={options}
              keyExtractor={(o) => o.value || '__empty'}
              renderItem={({ item }) => (
                <Pressable
                  accessibilityRole="button"
                  accessibilityState={{ selected: item.value === value }}
                  onPress={() => {
                    onChange(item.value);
                    setOpen(false);
                  }}
                  style={{
                    minHeight: 44,
                    justifyContent: 'center',
                    paddingHorizontal: 16,
                    backgroundColor: item.value === value ? colors.surfaceMuted : colors.surface,
                  }}
                >
                  <Text style={[text.body, { color: colors.textPrimary }]}>{item.label}</Text>
                </Pressable>
              )}
            />
          </View>
        </Pressable>
      </Modal>
    </View>
  );
}

/** Ô tick (chạm cả dòng), nền nhấn mạnh tùy chọn như các hộp xác nhận của web. */
export function Checkbox({
  label,
  checked,
  onChange,
  tone = 'plain',
}: {
  label: string;
  checked: boolean;
  onChange: (checked: boolean) => void;
  tone?: 'plain' | 'info' | 'warning';
}) {
  const bg =
    tone === 'info' ? colors.infoSoft : tone === 'warning' ? colors.warningSoft : 'transparent';
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityState={{ checked }}
      onPress={() => onChange(!checked)}
      style={{
        minHeight: 44,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 10,
        backgroundColor: bg,
        borderRadius: radius.control,
        padding: tone === 'plain' ? 0 : 12,
      }}
    >
      <View
        style={{
          width: 22,
          height: 22,
          borderRadius: 4,
          borderWidth: 2,
          borderColor: colors.borderStrong,
          backgroundColor: checked ? colors.primary : colors.surface,
          alignItems: 'center',
          justifyContent: 'center',
        }}
      >
        {checked ? <Text style={{ color: colors.creamOnDark, fontWeight: '700' }}>✓</Text> : null}
      </View>
      <Text style={[text.body, { color: colors.textPrimary, flex: 1 }]}>{label}</Text>
    </Pressable>
  );
}

/** Nhóm nút chọn một (thay tab hoặc nút bật/tắt của web). */
export function Segmented<T extends string>({
  options,
  value,
  onChange,
  label,
}: {
  options: Option<T>[];
  value: T;
  onChange: (value: T) => void;
  label?: string;
}) {
  return (
    <View
      accessibilityRole="tablist"
      accessibilityLabel={label}
      style={{ flexDirection: 'row', flexWrap: 'wrap', gap: 8 }}
    >
      {options.map((o) => {
        const active = o.value === value;
        return (
          <Pressable
            key={o.value}
            accessibilityRole="tab"
            accessibilityState={{ selected: active }}
            onPress={() => onChange(o.value)}
            style={{
              minHeight: size.button,
              paddingHorizontal: 14,
              justifyContent: 'center',
              borderRadius: radius.control,
              borderWidth: 1,
              borderColor: active ? colors.secondary : colors.borderStrong,
              backgroundColor: active ? colors.secondary : colors.surface,
            }}
          >
            <Text style={[text.label, { color: colors.textPrimary }]}>{o.label}</Text>
          </Pressable>
        );
      })}
    </View>
  );
}

/** Ô nhập nhiều dòng (textarea). */
export function TextArea({
  label,
  value,
  onChangeText,
  maxLength,
  placeholder,
  error,
}: {
  label: string;
  value: string;
  onChangeText: (value: string) => void;
  maxLength?: number;
  placeholder?: string;
  error?: string;
}) {
  return (
    <View style={{ gap: 6 }}>
      <Text style={[text.label, { color: colors.textPrimary }]}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        multiline
        textAlignVertical="top"
        value={value}
        onChangeText={onChangeText}
        maxLength={maxLength}
        placeholder={placeholder}
        placeholderTextColor={colors.textSecondary}
        style={{
          minHeight: 96,
          borderWidth: 1,
          borderColor: error ? colors.error : colors.border,
          borderRadius: radius.control,
          backgroundColor: colors.surface,
          padding: 12,
          color: colors.textPrimary,
          fontSize: text.body.fontSize,
        }}
      />
      {error ? <Text style={[text.caption, { color: colors.error }]}>{error}</Text> : null}
    </View>
  );
}

/** Trang trước / sau như web (page bắt đầu từ 0). */
export function Pager({
  page,
  total,
  pageSize,
  onChange,
}: {
  page: number;
  total: number;
  pageSize: number;
  onChange: (page: number) => void;
}) {
  if (total <= pageSize) return null;
  const pages = Math.ceil(total / pageSize);
  return (
    <View
      style={{
        flexDirection: 'row',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: 8,
      }}
    >
      <Button
        title="Trang trước"
        variant="secondary"
        disabled={page === 0}
        onPress={() => onChange(page - 1)}
      />
      <Text style={[text.caption, { color: colors.textSecondary }]}>
        Trang {page + 1}/{pages}
      </Text>
      <Button
        title="Trang sau"
        variant="secondary"
        disabled={page + 1 >= pages}
        onPress={() => onChange(page + 1)}
      />
    </View>
  );
}

/** Nhãn nhỏ dạng viên thuốc. */
export function Pill({
  label,
  bg = colors.surfaceMuted,
  fg = colors.textPrimary,
}: {
  label: string;
  bg?: string;
  fg?: string;
}) {
  return (
    <Text
      style={[
        text.caption,
        {
          backgroundColor: bg,
          color: fg,
          fontWeight: '600',
          paddingHorizontal: 8,
          borderRadius: 999,
          overflow: 'hidden',
          alignSelf: 'flex-start',
        },
      ]}
    >
      {label}
    </Text>
  );
}

/** Dòng nhãn: giá trị. */
export function InfoRow({ label, children }: { label: string; children: ReactNode }) {
  return (
    <View style={{ flexDirection: 'row', gap: 8 }}>
      <Text style={[text.label, { color: colors.textSecondary, width: 120 }]}>{label}</Text>
      <View style={{ flex: 1 }}>
        {typeof children === 'string' ? (
          <Text style={[text.body, { color: colors.textPrimary }]}>{children}</Text>
        ) : (
          children
        )}
      </View>
    </View>
  );
}

/** Hỏi lại trước thao tác khó hoàn tác (xóa, khóa, khôi phục); dùng hộp thoại của hệ điều hành. */
export function confirmAction(
  title: string,
  message: string,
  onConfirm: () => void,
  confirmLabel = 'Đồng ý',
) {
  Alert.alert(title, message, [
    { text: 'Hủy', style: 'cancel' },
    { text: confirmLabel, style: 'destructive', onPress: onConfirm },
  ]);
}

/** Lỗi API thành câu hiển thị (như errorText của web). */
export function errorText(error: unknown): string {
  return error instanceof Error ? error.message : 'Có lỗi xảy ra, vui lòng thử lại.';
}

export function ErrorText({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <Text accessibilityRole="alert" style={[text.body, { color: colors.error }]}>
      {errorText(error)}
    </Text>
  );
}

/** YYYY-MM-DD theo giờ máy (như toLocaleDateString('sv-SE') của web). */
export function todayIso(): string {
  const d = new Date();
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Ô ngày dạng YYYY-MM-DD (bộ thư viện đã duyệt không có ô chọn ngày của hệ điều hành). */
export const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
export const TIME_PATTERN = /^([01]\d|2[0-3]):[0-5]\d$/;

export function isValidDate(value: string): boolean {
  if (!DATE_PATTERN.test(value)) return false;
  const d = new Date(`${value}T00:00:00Z`);
  return !Number.isNaN(d.getTime()) && d.toISOString().slice(0, 10) === value;
}
