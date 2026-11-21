import { ScrollView, StyleSheet, View, type ViewStyle } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import type { ReactNode } from 'react';
import { spacing, useTheme } from '../lib/theme';

interface Props {
  children: ReactNode;
  footer?: ReactNode;
  scroll?: boolean;
  contentStyle?: ViewStyle;
}

/** Pantalla base: fondo negro, ancho máximo legible y pie fijo al alcance del pulgar. */
export function Screen({ children, footer, scroll = true, contentStyle }: Props) {
  const theme = useTheme();
  const inner = <View style={[styles.content, contentStyle]}>{children}</View>;
  return (
    <SafeAreaView edges={['bottom', 'left', 'right']} style={[styles.root, { backgroundColor: theme.background }]}>
      {scroll ? <ScrollView contentContainerStyle={styles.scroll}>{inner}</ScrollView> : <View style={styles.scroll}>{inner}</View>}
      {footer ? (
        <View style={[styles.footer, { borderTopColor: theme.border, backgroundColor: theme.background }]}>
          <View style={styles.footerInner}>{footer}</View>
        </View>
      ) : null}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  root: { flex: 1 },
  scroll: { flexGrow: 1, alignItems: 'center' },
  content: { width: '100%', maxWidth: 640, padding: spacing(4), gap: spacing(4) },
  footer: { borderTopWidth: 2, alignItems: 'center' },
  footerInner: { width: '100%', maxWidth: 640, padding: spacing(4) },
});
