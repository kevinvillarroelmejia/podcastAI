import { useCallback, useState } from 'react';
import { Pressable, StyleSheet, View } from 'react-native';
import { Stack, router, useFocusEffect } from 'expo-router';
import { CircleUser, Plus } from 'lucide-react-native';
import { api, ApiError, type PodcastSummary } from '../lib/api';
import { formatDuration, modeLabel, statusLabel } from '../lib/format';
import { spacing, useTheme } from '../lib/theme';
import { AppText } from '../components/AppText';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Screen } from '../components/Screen';
import { PixelPortrait, RetroLogo } from '../components/Pixel';

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

        <RetroLogo />
        <Cast />

        {items && items.length === 0 ? <Welcome /> : null}

        {items && items.length > 0 ? (
          <View style={{ gap: spacing(3) }}>
            <AppText variant="heading" style={{ textAlign: 'center' }}>* * * Tu biblioteca * * *</AppText>
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

/** «Protagonizado por...»: los dos presentadores con su retrato pixelado. */
function Cast() {
  const cast = [
    { who: 'LIA' as const, name: 'LÍA', role: 'la presentadora' },
    { who: 'MARCO' as const, name: 'MARCO', role: 'el experto' },
  ];
  return (
    <View style={{ gap: spacing(3), alignItems: 'center' }}>
      <AppText variant="caption" tone="secondary">Protagonizado por...</AppText>
      <View style={{ flexDirection: 'row', gap: spacing(6), justifyContent: 'center' }}>
        {cast.map((c) => (
          <View key={c.who} style={{ alignItems: 'center', gap: spacing(2) }} accessible accessibilityLabel={`${c.name}, ${c.role}`}>
            <PixelPortrait who={c.who} size={112} />
            <AppText variant="heading" style={{ fontSize: 11 }}>{c.name}</AppText>
            <AppText variant="caption" tone="secondary">{c.role}</AppText>
          </View>
        ))}
      </View>
    </View>
  );
}

function Welcome() {
  const steps = [
    { title: 'Sube un PDF de clase', text: 'Apuntes, temas o artículos. Lo leemos página a página.' },
    { title: 'Lía y Marco lo convierten en charla', text: 'Fieles a tu PDF y en tu idioma.' },
    { title: 'Escucha en segundos', text: 'Empieza a sonar en cuanto está el primer bloque.' },
  ];
  return (
    <Card style={{ gap: spacing(3) }}>
      {steps.map((s, i) => (
        <View key={s.title} style={{ gap: spacing(1) }}>
          <AppText variant="label">{`> ${i + 1}. ${s.title}`}</AppText>
          <AppText tone="secondary">{`  ${s.text}`}</AppText>
        </View>
      ))}
      <AppText tone="secondary">{'_'}</AppText>
    </Card>
  );
}

const styles = StyleSheet.create({
  headerButton: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
  track: { height: 8, overflow: 'hidden' },
  fill: { height: 8 },
});
