import { useColorScheme } from 'react-native';

/** Paleta mate del documento de diseño. Todos los textos superan 4,5:1 (WCAG AA). */
const light = {
  background: '#F4F1EC', // hueso
  surface: '#FBFAF7',
  border: '#E4DFD6',
  text: '#1F2328', // grafito
  textSecondary: '#5B6169',
  accent: '#3F6B5B', // salvia
  accentSoft: '#E3ECE7',
  onAccent: '#FFFFFF',
  error: '#A3473B',
  errorSoft: '#F6E5E1',
};

const dark: typeof light = {
  background: '#16181B', // carbón
  surface: '#202327',
  border: '#2E3237',
  text: '#ECEAE5',
  textSecondary: '#A8ADB3',
  accent: '#8DB5A3',
  accentSoft: '#26332D',
  onAccent: '#16181B',
  error: '#E08A7C',
  errorSoft: '#3A2522',
};

export type Theme = typeof light;

export const radius = 12;
export const spacing = (n: number) => n * 4;

export const fonts = {
  regular: 'Inter_400Regular',
  medium: 'Inter_500Medium',
  semibold: 'Inter_600SemiBold',
  bold: 'Inter_700Bold',
};

export function useTheme(): Theme {
  return useColorScheme() === 'dark' ? dark : light;
}
