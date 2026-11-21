import { Text, type TextProps } from 'react-native';
import { fonts, useTheme } from '../lib/theme';

type Variant = 'title' | 'heading' | 'body' | 'label' | 'caption';

const sizes: Record<Variant, { fontSize: number; lineHeight: number; fontFamily: string }> = {
  title: { fontSize: 18, lineHeight: 30, fontFamily: fonts.pixel },
  heading: { fontSize: 13, lineHeight: 22, fontFamily: fonts.pixel },
  body: { fontSize: 22, lineHeight: 26, fontFamily: fonts.regular },
  label: { fontSize: 22, lineHeight: 26, fontFamily: fonts.medium },
  caption: { fontSize: 19, lineHeight: 22, fontFamily: fonts.regular },
};

interface Props extends TextProps {
  variant?: Variant;
  tone?: 'primary' | 'secondary' | 'accent' | 'error' | 'onAccent';
}

/** Texto de la app: VT323 (terminal) y Press Start 2P en títulos; respeta el tamaño de letra del sistema. */
export function AppText({ variant = 'body', tone = 'primary', style, ...rest }: Props) {
  const theme = useTheme();
  const color = {
    primary: theme.text,
    secondary: theme.textSecondary,
    accent: theme.accent,
    error: theme.error,
    onAccent: theme.onAccent,
  }[tone];
  return (
    <Text
      maxFontSizeMultiplier={2}
      accessibilityRole={variant === 'title' || variant === 'heading' ? 'header' : undefined}
      style={[sizes[variant], { color }, style]}
      {...rest}
    />
  );
}
