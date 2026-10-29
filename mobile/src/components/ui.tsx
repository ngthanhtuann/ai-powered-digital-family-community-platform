// Component dùng chung theo design system: Button, TextField, Notice (thay Toast), Screen, Card.
import type { ReactNode } from 'react';
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
  type TextInputProps,
} from 'react-native';
import { colors, radius, size, spacing, text } from '../theme';

type ButtonVariant = 'primary' | 'secondary' | 'danger';

export function Button({
  title,
  onPress,
  variant = 'primary',
  disabled,
  loading,
}: {
  title: string;
  onPress: () => void;
  variant?: ButtonVariant;
  disabled?: boolean;
  loading?: boolean;
}) {
  const inactive = disabled || loading;
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled: inactive, busy: loading }}
      onPress={onPress}
      disabled={inactive}
      style={({ pressed }) => [
        styles.button,
        styles[variant],
        pressed && variant === 'primary' && { backgroundColor: colors.goldHover },
        pressed && variant === 'secondary' && { backgroundColor: colors.surfaceMuted },
        inactive && styles.inactive,
      ]}
    >
      {loading ? (
        <ActivityIndicator color={variant === 'danger' ? colors.surface : colors.textPrimary} />
      ) : (
        <Text
          style={[
            text.label,
            { color: variant === 'danger' ? colors.surface : colors.textPrimary },
          ]}
        >
          {title}
        </Text>
      )}
    </Pressable>
  );
}

export function TextField({
  label,
  error,
  style,
  editable = true,
  ...props
}: TextInputProps & { label: string; error?: string }) {
  return (
    <View style={styles.field}>
      <Text style={[text.label, { color: colors.textPrimary }]}>{label}</Text>
      <TextInput
        accessibilityLabel={label}
        placeholderTextColor={colors.textSecondary}
        editable={editable}
        style={[
          styles.input,
          !editable && { backgroundColor: colors.surfaceMuted, color: colors.textSecondary },
          error ? { borderColor: colors.error } : null,
          style,
        ]}
        {...props}
      />
      {error ? <Text style={[text.caption, { color: colors.error }]}>{error}</Text> : null}
    </View>
  );
}

type NoticeVariant = 'success' | 'error' | 'info' | 'warning';
const NOTICE: Record<NoticeVariant, { bg: string; border: string; color: string }> = {
  success: { bg: colors.successSoft, border: colors.border, color: colors.textPrimary },
  error: { bg: colors.errorSoft, border: colors.error, color: colors.error },
  info: { bg: colors.infoSoft, border: colors.info, color: colors.info },
  warning: { bg: colors.warningSoft, border: colors.warning, color: colors.warning },
};

export interface NoticeMessage {
  variant: NoticeVariant;
  title: string;
  message: string;
}

export function Notice({ variant, title, message }: NoticeMessage) {
  const s = NOTICE[variant];
  return (
    <View
      accessibilityRole={variant === 'error' ? 'alert' : 'summary'}
      style={[styles.notice, { backgroundColor: s.bg, borderColor: s.border }]}
    >
      <Text style={[text.label, { color: s.color }]}>{title}</Text>
      <Text style={[text.body, { color: s.color }]}>{message}</Text>
    </View>
  );
}

/** Khung trang cuộn được, tránh bàn phím che ô nhập. */
export function Screen({ children }: { children: ReactNode }) {
  return (
    <KeyboardAvoidingView
      style={{ flex: 1, backgroundColor: colors.background }}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
    >
      <ScrollView contentContainerStyle={styles.screen} keyboardShouldPersistTaps="handled">
        {children}
      </ScrollView>
    </KeyboardAvoidingView>
  );
}

export function Card({
  title,
  description,
  children,
}: {
  title?: string;
  description?: string;
  children: ReactNode;
}) {
  return (
    <View style={styles.card}>
      {title ? (
        <Text style={[text.sectionTitle, { color: colors.textPrimary }]}>{title}</Text>
      ) : null}
      {description ? (
        <Text style={[text.body, { color: colors.textSecondary }]}>{description}</Text>
      ) : null}
      {children}
    </View>
  );
}

export function Loading({ label = 'Đang tải...' }: { label?: string }) {
  return (
    <View style={styles.center} accessibilityRole="progressbar" accessibilityLabel={label}>
      <ActivityIndicator color={colors.primary} />
      <Text style={[text.body, { color: colors.textSecondary }]}>{label}</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  button: {
    minHeight: size.button,
    borderRadius: radius.control,
    borderWidth: 1,
    paddingHorizontal: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  primary: { backgroundColor: colors.secondary, borderColor: colors.secondary },
  secondary: { backgroundColor: colors.surface, borderColor: colors.borderStrong },
  danger: { backgroundColor: colors.error, borderColor: colors.error },
  inactive: { opacity: 0.5 },
  field: { gap: 6 },
  input: {
    minHeight: size.input,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radius.control,
    backgroundColor: colors.surface,
    paddingHorizontal: 12,
    color: colors.textPrimary,
    fontSize: text.body.fontSize,
  },
  notice: { borderWidth: 1, borderRadius: radius.control, padding: 12, gap: 2 },
  screen: { padding: spacing.page, gap: spacing.gap, flexGrow: 1 },
  card: {
    backgroundColor: colors.surface,
    borderColor: colors.border,
    borderWidth: 1,
    borderRadius: radius.card,
    padding: 20,
    gap: 16,
  },
  center: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    backgroundColor: colors.background,
  },
});
