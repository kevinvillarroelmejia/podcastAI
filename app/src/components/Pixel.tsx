import { View } from 'react-native';
import Svg, { Rect } from 'react-native-svg';
import { useTheme } from '../lib/theme';
import { AppText } from './AppText';

/**
 * Retratos pixelados de 16×16. '#' fósforo, '+' verde tenue, ' ' negro y '.' fondo tramado,
 * como los gráficos de las pantallas de los 80.
 */
const SPRITES = {
  LIA: [
    '................',
    '....########....',
    '...##########...',
    '..####++++####..',
    '..###+    +###..',
    '.###  #  #  ###.',
    '.###        ###.',
    '.###   ##   ###.',
    '..##        ###.',
    '..##  ####  ###.',
    '..###      ####.',
    '..####    #####.',
    '..###++++++###..',
    '.##++++++++++##.',
    '##++++++++++++##',
    '#++++++++++++++#',
  ],
  MARCO: [
    '................',
    '................',
    '.....######.....',
    '....########....',
    '...###++++###...',
    '...#        #...',
    '..####   ####...',
    '..#  #####  #...',
    '..####   ####...',
    '...#   ##   #...',
    '...#++++++++#...',
    '...#++ ## ++#...',
    '....#++++++#....',
    '..####++++####..',
    '.##++++++++++##.',
    '##++++++++++++##',
  ],
} as const;

export type Character = keyof typeof SPRITES;

export function PixelPortrait({ who, size = 96, dim = false }: { who: Character; size?: number; dim?: boolean }) {
  const theme = useTheme();
  const rows = SPRITES[who];
  const rects: React.ReactNode[] = [];
  rows.forEach((row, y) => {
    for (let x = 0; x < 16; x++) {
      const c = row[x];
      if (c === '#') rects.push(<Rect key={`${x}-${y}`} x={x} y={y} width={1} height={1} fill={theme.accent} />);
      if (c === '+') rects.push(<Rect key={`${x}-${y}`} x={x} y={y} width={1} height={1} fill={theme.textSecondary} />);
      if (c === '.') {
        // Trama: un píxel tenue de cada dos, a media resolución.
        rects.push(<Rect key={`${x}-${y}a`} x={x} y={y} width={0.5} height={0.5} fill={theme.border} />);
        rects.push(<Rect key={`${x}-${y}b`} x={x + 0.5} y={y + 0.5} width={0.5} height={0.5} fill={theme.border} />);
      }
    }
  });
  return (
    <View
      style={{ borderWidth: 2, borderColor: theme.border, opacity: dim ? 0.45 : 1 }}
      importantForAccessibility="no-hide-descendants"
      accessibilityElementsHidden
    >
      <Svg width={size} height={size} viewBox="0 0 16 16">
        {rects}
      </Svg>
    </View>
  );
}

/** Logo: «podcastAI» en letras de píxel entre dos filas de cruces, como un título de juego antiguo. */
export function RetroLogo() {
  const theme = useTheme();
  const crosses = '+ + + + + + + + + + + + + + +';
  return (
    <View style={{ alignItems: 'center', gap: 8 }} accessibilityRole="header" accessibilityLabel="podcastAI">
      <AppText variant="caption" tone="secondary" importantForAccessibility="no" numberOfLines={1}>
        {crosses}
      </AppText>
      <AppText
        variant="title"
        style={{ fontSize: 28, lineHeight: 40, textShadowColor: theme.border, textShadowOffset: { width: 3, height: 3 }, textShadowRadius: 0 }}
        accessibilityRole="text"
        importantForAccessibility="no"
      >
        podcastAI
      </AppText>
      <AppText variant="caption" tone="secondary" importantForAccessibility="no" numberOfLines={1}>
        {crosses}
      </AppText>
      <AppText variant="caption" style={{ textAlign: 'center' }}>
        Tu PDF convertido en podcast ✲ Voces de Lía y Marco
      </AppText>
    </View>
  );
}
