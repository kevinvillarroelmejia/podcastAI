import { View, type ViewProps } from 'react-native';
import { radius, spacing, useTheme } from '../lib/theme';

/** Recuadro de terminal: borde verde de 2 px y esquinas rectas. */
export function Card({ style, ...rest }: ViewProps) {
  const theme = useTheme();
  return (
    <View
      style={[{ backgroundColor: theme.surface, borderRadius: radius, padding: spacing(4), borderWidth: 2, borderColor: theme.border }, style]}
      {...rest}
    />
  );
}
