import { Text, type TextProps } from 'react-native';
import { fonts, useTheme } from '../lib/theme';

type Variant = 'title' | 'heading' | 'body' | 'label' | 'caption';

const sizes: Record<Variant, { fontSize: number; lineHeight: number; fontFamily: string }> = {
  title: { fontSize: 28, lineHeight: 34, fontFamily: fonts.bold },
  heading: { fontSize: 20, lineHeight: 26, fontFamily: fonts.semibold },
  body: { fontSize: 16, lineHeight: 24, fontFamily: fonts.regular },
  label: { fontSize: 16, lineHeight: 22, fontFamily: fonts.medium },
  caption: { fontSize: 14, lineHeight: 20, fontFamily: fonts.regular },
};

interface Props extends TextProps {
  variant?: Variant;
  tone?: 'primary' | 'secondary' | 'accent' | 'error' | 'onAccent';
}

/** Texto de la app: Inter, mínimo 16 px en cuerpo y respeta el tamaño de letra del sistema. */
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
