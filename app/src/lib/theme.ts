/**
 * Estilo terminal retro de fósforo verde: fondo negro, texto verde y fuentes pixeladas.
 * Un solo tema (ya es oscuro). Todos los textos superan 7:1 de contraste sobre el negro.
 */
const phosphor = {
  background: '#000000',
  surface: '#040C05',
  border: '#1F8F3E',
  text: '#5CFF7A', // fósforo
  textSecondary: '#33C457',
  accent: '#5CFF7A',
  accentSoft: '#0B2A12',
  onAccent: '#000000',
  error: '#FF8A66',
  errorSoft: '#2A0F08',
};

export type Theme = typeof phosphor;

/** Esquinas rectas, como en una pantalla de los 80. */
export const radius = 0;
export const spacing = (n: number) => n * 4;

export const fonts = {
  regular: 'VT323_400Regular',
  medium: 'VT323_400Regular',
  semibold: 'VT323_400Regular',
  bold: 'VT323_400Regular',
  pixel: 'PressStart2P_400Regular',
};

export function useTheme(): Theme {
  return phosphor;
}
