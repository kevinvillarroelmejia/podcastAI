import { useEffect, useState } from 'react';
import { View } from 'react-native';
import { router } from 'expo-router';
import * as DocumentPicker from 'expo-document-picker';
import { FileUp, Lock } from 'lucide-react-native';
import { api, ApiError, type AccountView, type Mode, type PickedPdf } from '../lib/api';
import { deviceLanguage, LANGUAGES } from '../lib/format';
import { spacing, useTheme } from '../lib/theme';
import { AppText } from '../components/AppText';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Chip } from '../components/Chip';
import { Screen } from '../components/Screen';

const MAX_BYTES = 20 * 1024 * 1024;

/** Nuevo podcast: elegir PDF, modo e idioma. */
export default function NewPodcast() {
  const theme = useTheme();
  const [pdf, setPdf] = useState<PickedPdf | null>(null);
  const [mode, setMode] = useState<Mode>('RESUMEN');
  const [language, setLanguage] = useState(deviceLanguage());
  const [account, setAccount] = useState<AccountView | null>(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    api.me().then(setAccount).catch(() => setAccount(null));
  }, []);

  const premium = account?.plan === 'PREMIUM';

  async function pick() {
    setError(null);
    const result = await DocumentPicker.getDocumentAsync({ type: 'application/pdf', copyToCacheDirectory: true });
    if (!result.canceled && result.assets.length > 0) {
      const asset = result.assets[0];
      if (asset.size && asset.size > MAX_BYTES) {
        setError('El PDF supera los 20 MB.');
      } else {
        setPdf({ name: asset.name, uri: asset.uri, size: asset.size, file: asset.file });
      }
    }
  }

  async function submit() {
    if (!pdf) return;
    setSending(true);
    setError(null);
    try {
      const created = await api.create(pdf, mode, language);
      router.replace(`/podcast/${created.id}`);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se ha podido crear el podcast.');
      setSending(false);
    }
  }

  const usage = !account
    ? null
    : premium
      ? `Consume hasta ${mode === 'RESUMEN' ? 8 : 20} min de los ${Math.max(0, Math.round(account.monthlyMinutes - account.minutesUsed))} que te quedan este mes.`
      : account.freePodcastsLeft > 0
        ? 'Usa tu resumen gratis (hasta 8 minutos).'
        : 'Ya has usado tu podcast gratis. El plan Premium cuesta 10 €/mes con 120 minutos.';

  return (
    <Screen
      footer={
        <Button
          label={sending ? 'Subiendo…' : 'Crear podcast'}
          onPress={submit}
          disabled={!pdf || (!premium && mode === 'A_FONDO')}
          loading={sending}
          accessibilityHint="Sube el PDF y empieza a generar el podcast"
        />
      }
    >
      <View style={{ gap: spacing(2) }}>
        <AppText variant="heading">1. Tu PDF</AppText>
        <Card style={{ gap: spacing(3) }}>
          <AppText tone={pdf ? 'primary' : 'secondary'} numberOfLines={2}>
            {pdf ? pdf.name : 'Hasta 20 MB y 100 páginas, con texto seleccionable.'}
          </AppText>
          <Button
            label={pdf ? 'Cambiar PDF' : 'Elegir PDF'}
            variant="secondary"
            icon={<FileUp color={theme.text} size={20} strokeWidth={1.75} />}
            onPress={pick}
          />
        </Card>
      </View>

      <View style={{ gap: spacing(2) }} accessibilityRole="radiogroup" accessibilityLabel="Modo">
        <AppText variant="heading">2. Modo</AppText>
        <View style={{ flexDirection: 'row', gap: spacing(2), flexWrap: 'wrap' }}>
          <Chip label="Resumen · 5-8 min" selected={mode === 'RESUMEN'} onPress={() => setMode('RESUMEN')} />
          <Chip
            label={premium ? 'A fondo · 12-20 min' : 'A fondo · Premium'}
            selected={mode === 'A_FONDO'}
            onPress={() => setMode('A_FONDO')}
          />
        </View>
        {!premium && mode === 'A_FONDO' ? (
          <View style={{ flexDirection: 'row', gap: spacing(2), alignItems: 'center' }}>
            <Lock color={theme.textSecondary} size={16} />
            <AppText variant="caption" tone="secondary">
              El modo A fondo es del plan Premium.
            </AppText>
          </View>
        ) : null}
      </View>

      <View style={{ gap: spacing(2) }} accessibilityRole="radiogroup" accessibilityLabel="Idioma del podcast">
        <AppText variant="heading">3. Idioma del podcast</AppText>
        <AppText variant="caption" tone="secondary">
          Lía y Marco hablarán en este idioma aunque el PDF esté en otro.
        </AppText>
        <View style={{ flexDirection: 'row', gap: spacing(2), flexWrap: 'wrap' }}>
          {LANGUAGES.map((l) => (
            <Chip key={l.code} label={l.label} selected={language === l.code} onPress={() => setLanguage(l.code)} />
          ))}
        </View>
      </View>

      {usage ? (
        <AppText variant="caption" tone="secondary">
          {usage}
        </AppText>
      ) : null}

      {error ? (
        <Card style={{ backgroundColor: theme.errorSoft, borderColor: theme.errorSoft }}>
          <AppText tone="error" accessibilityRole="alert" accessibilityLiveRegion="assertive">
            {error}
          </AppText>
        </Card>
      ) : null}
    </Screen>
  );
}
