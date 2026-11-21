import { Pressable, StyleSheet } from 'react-native';
import { radius, spacing, useTheme } from '../lib/theme';
import { AppText } from './AppText';

interface Props {
  label: string;
  selected: boolean;
  onPress: () => void;
  accessibilityLabel?: string;
}

/** Opción seleccionable. El estado se indica con borde, fondo y texto, no solo con color. */
export function Chip({ label, selected, onPress, accessibilityLabel }: Props) {
  const theme = useTheme();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="radio"
      accessibilityState={{ selected, checked: selected }}
      accessibilityLabel={accessibilityLabel ?? label}
      style={[
        styles.chip,
        {
          backgroundColor: selected ? theme.accent : theme.background,
          borderColor: selected ? theme.accent : theme.border,
          borderWidth: 2,
        },
      ]}
    >
      <AppText variant="label" tone={selected ? 'onAccent' : 'primary'}>
        {selected ? `> ${label}` : label}
      </AppText>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  chip: {
    minHeight: 48,
    paddingHorizontal: spacing(4),
    borderRadius: radius,
    justifyContent: 'center',
  },
});
