import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Stack, router, useFocusEffect } from 'expo-router';
import { CircleUser, FileText, Headphones, Plus, Sparkles } from 'lucide-react-native';
import { api, ApiError, type PodcastSummary } from '../lib/api';
import { formatDuration, modeLabel, statusLabel } from '../lib/format';
import { spacing, useTheme } from '../lib/theme';
import { AppText } from '../components/AppText';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Screen } from '../components/Screen';

/** Biblioteca: la pantalla de inicio. */
export default function Library() {
  const theme = useTheme();
  const [items, setItems] = useState<PodcastSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    try {
      setItems(await api.list());
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se ha podido cargar la biblioteca.');
    }
  }, []);

  useFocusEffect(
    useCallback(() => {
      load();
      const busy = setInterval(load, 4000);
      return () => clearInterval(busy);
    }, [load]),
  );

  return (
    <>
      <Stack.Screen
        options={{
          headerRight: () => (
            <Pressable
              onPress={() => router.push('/cuenta')}
              accessibilityRole="button"
              accessibilityLabel="Cuenta y plan"
              hitSlop={12}
              style={styles.headerButton}
            >
              <CircleUser color={theme.text} size={26} strokeWidth={1.75} />
            </Pressable>
          ),
        }}
      />
      <Screen
        footer={
          <Button
            label="Nuevo podcast"
            icon={<Plus color={theme.onAccent} size={22} strokeWidth={2} />}
            onPress={() => router.push('/nuevo')}
          />
        }
      >
        {error ? (
          <Card style={{ backgroundColor: theme.errorSoft, borderColor: theme.errorSoft }}>
            <AppText tone="error" accessibilityRole="alert">{error}</AppText>
          </Card>
        ) : null}

        {items && items.length === 0 ? <Welcome /> : null}

        {items && items.length > 0 ? (
          <View style={{ gap: spacing(3) }}>
            <AppText variant="title">Tu biblioteca</AppText>
            {items.map((p) => (
              <PodcastRow key={p.id} podcast={p} />
            ))}
          </View>
        ) : null}
      </Screen>
    </>
  );
}

function PodcastRow({ podcast }: { podcast: PodcastSummary }) {
  const theme = useTheme();
  const generating = podcast.status !== 'READY' && podcast.status !== 'FAILED';
  const progress = podcast.totalBlocks > 0 ? podcast.readyBlocks / podcast.totalBlocks : 0;
  const meta = [modeLabel[podcast.mode], podcast.durationMs > 0 ? formatDuration(podcast.durationMs) : null, statusLabel[podcast.status]]
    .filter(Boolean)
    .join(' · ');
  return (
    <Pressable
      onPress={() => router.push(`/podcast/${podcast.id}`)}
      accessibilityRole="button"
      accessibilityLabel={`${podcast.title}. ${meta}`}
      accessibilityHint="Abre el reproductor"
    >
      {({ pressed }) => (
        <Card style={{ opacity: pressed ? 0.85 : 1, gap: spacing(2) }}>
          <AppText variant="label" numberOfLines={2}>
            {podcast.title}
          </AppText>
          <AppText variant="caption" tone={podcast.status === 'FAILED' ? 'error' : 'secondary'}>
            {meta}
          </AppText>
          {generating ? (
            <View
              style={[styles.track, { backgroundColor: theme.accentSoft }]}
              accessibilityRole="progressbar"
              accessibilityValue={{ min: 0, max: podcast.totalBlocks || 1, now: podcast.readyBlocks }}
            >
              <View style={[styles.fill, { backgroundColor: theme.accent, width: `${Math.max(4, progress * 100)}%` }]} />
            </View>
          ) : null}
        </Card>
      )}
    </Pressable>
  );
}

function Welcome() {
  const theme = useTheme();
  const steps = [
    { icon: <FileText color={theme.accent} size={28} strokeWidth={1.5} />, title: 'Sube un PDF de clase', text: 'Apuntes, temas o artículos. Lo leemos página a página.' },
    { icon: <Sparkles color={theme.accent} size={28} strokeWidth={1.5} />, title: 'Lía y Marco lo convierten en charla', text: 'Una presentadora curiosa y un experto cercano, fieles a tu PDF y en tu idioma.' },
    { icon: <Headphones color={theme.accent} size={28} strokeWidth={1.5} />, title: 'Escucha en segundos', text: 'Empieza a sonar en cuanto está el primer bloque. Tu primer resumen es gratis.' },
  ];
  return (
    <View style={{ gap: spacing(4) }}>
      <AppText variant="title">Estudia escuchando</AppText>
      {steps.map((s) => (
        <Card key={s.title} style={{ flexDirection: 'row', gap: spacing(4), alignItems: 'flex-start' }}>
          <View importantForAccessibility="no" accessibilityElementsHidden>{s.icon}</View>
          <View style={{ flex: 1, gap: spacing(1) }}>
            <AppText variant="label">{s.title}</AppText>
            <AppText tone="secondary">{s.text}</AppText>
          </View>
        </Card>
      ))}
    </View>
  );
}

const styles = StyleSheet.create({
  headerButton: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
  track: { height: 6, borderRadius: 3, overflow: 'hidden' },
  fill: { height: 6, borderRadius: 3 },
});
