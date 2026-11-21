import { ActivityIndicator, Pressable, StyleSheet, View, type ViewStyle } from 'react-native';
import type { ReactNode } from 'react';
import { radius, spacing, useTheme } from '../lib/theme';
import { AppText } from './AppText';

interface Props {
  label: string;
  onPress: () => void;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  icon?: ReactNode;
  disabled?: boolean;
  loading?: boolean;
  accessibilityHint?: string;
  style?: ViewStyle;
}

/** Botón con objetivo táctil de 48 px como mínimo. */
export function Button({ label, onPress, variant = 'primary', icon, disabled, loading, accessibilityHint, style }: Props) {
  const theme = useTheme();
  const inactive = disabled || loading;
  const palette = {
    primary: { bg: theme.accent, fg: 'onAccent' as const, border: theme.accent },
    secondary: { bg: theme.background, fg: 'primary' as const, border: theme.accent },
    ghost: { bg: 'transparent', fg: 'accent' as const, border: 'transparent' },
    danger: { bg: theme.background, fg: 'error' as const, border: theme.error },
  }[variant];
  return (
    <Pressable
      onPress={onPress}
      disabled={inactive}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={accessibilityHint}
      accessibilityState={{ disabled: !!inactive, busy: !!loading }}
      style={({ pressed }) => [
        styles.base,
        { backgroundColor: palette.bg, borderColor: palette.border, opacity: inactive ? 0.55 : pressed ? 0.85 : 1 },
        style,
      ]}
    >
      <View style={styles.row}>
        {loading ? <ActivityIndicator color={variant === 'primary' ? theme.onAccent : theme.accent} /> : icon}
        <AppText variant="label" tone={palette.fg}>
          {label}
        </AppText>
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  base: {
    minHeight: 52,
    borderRadius: radius,
    borderWidth: 2,
    paddingHorizontal: spacing(5),
    justifyContent: 'center',
  },
  row: { flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: spacing(2) },
});
