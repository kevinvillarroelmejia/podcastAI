import type { Mode, PodcastStatus } from './api';

export function formatDuration(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${m}:${s.toString().padStart(2, '0')}`;
}

export function spokenDuration(ms: number): string {
  const minutes = Math.round(ms / 60000);
  return minutes < 1 ? 'menos de un minuto' : `${minutes} ${minutes === 1 ? 'minuto' : 'minutos'}`;
}

export const modeLabel: Record<Mode, string> = {
  RESUMEN: 'Resumen',
  A_FONDO: 'A fondo',
};

export const statusLabel: Record<PodcastStatus, string> = {
  QUEUED: 'En cola',
  MAPPING: 'Leyendo el PDF',
  GENERATING: 'Generando',
  READY: 'Listo',
  FAILED: 'Con error',
};

export const LANGUAGES: { code: string; label: string }[] = [
  { code: 'es', label: 'Español' },
  { code: 'en', label: 'English' },
  { code: 'ca', label: 'Català' },
  { code: 'gl', label: 'Galego' },
  { code: 'eu', label: 'Euskara' },
  { code: 'fr', label: 'Français' },
  { code: 'de', label: 'Deutsch' },
  { code: 'it', label: 'Italiano' },
  { code: 'pt', label: 'Português' },
];

export function deviceLanguage(): string {
  let code = 'es';
  try {
    const locale = Intl.DateTimeFormat().resolvedOptions().locale.slice(0, 2).toLowerCase();
    if (LANGUAGES.some((l) => l.code === locale)) code = locale;
  } catch {
    // se queda en español
  }
  return code;
}
