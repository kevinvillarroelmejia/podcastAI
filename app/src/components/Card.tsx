import { View, type ViewProps } from 'react-native';
import { radius, spacing, useTheme } from '../lib/theme';

/** Tarjeta sin sombras: la capa se separa con el tono de superficie. */
export function Card({ style, ...rest }: ViewProps) {
  const theme = useTheme();
  return (
    <View
      style={[{ backgroundColor: theme.surface, borderRadius: radius, padding: spacing(4), borderWidth: 1, borderColor: theme.border }, style]}
      {...rest}
    />
  );
}
