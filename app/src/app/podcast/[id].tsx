import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { AccessibilityInfo, ActivityIndicator, Linking, Platform, Pressable, Share, StyleSheet, View } from 'react-native';
import { Stack, router, useLocalSearchParams } from 'expo-router';
import { setAudioModeAsync, useAudioPlayer, useAudioPlayerStatus } from 'expo-audio';
import { Check, Download, Pause, Play, RotateCcw, RotateCw, Share2, Trash2 } from 'lucide-react-native';
import { api, ApiError, type BlockView, type PodcastDetail } from '../../lib/api';
import { formatDuration, modeLabel, spokenDuration } from '../../lib/format';
import { radius, spacing, useTheme } from '../../lib/theme';
import { AppText } from '../../components/AppText';
import { Button } from '../../components/Button';
import { Card } from '../../components/Card';
import { Screen } from '../../components/Screen';
import { PixelPortrait } from '../../components/Pixel';

const RATES = [0.75, 1, 1.25, 1.5, 2];
const POLL_MS = 1500;

/** Generando + reproductor: empieza a sonar en cuanto existe el primer bloque. */
export default function PodcastScreen() {
  const { id } = useLocalSearchParams<{ id: string }>();
  const theme = useTheme();
  const [detail, setDetail] = useState<PodcastDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [current, setCurrent] = useState<number | null>(null);
  const [waitingNext, setWaitingNext] = useState(false);
  const [rate, setRate] = useState(1);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const [tab, setTab] = useState<'escuchar' | 'guion'>('escuchar');
  const pendingSeek = useRef<number | null>(null);
  const announced = useRef(0);

  const player = useAudioPlayer();
  const status = useAudioPlayerStatus(player);

  const finished = detail?.podcast.status === 'READY' || detail?.podcast.status === 'FAILED';

  const load = useCallback(async () => {
    try {
      const d = await api.detail(id);
      setDetail(d);
      setError(null);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se ha podido cargar el podcast.');
    }
  }, [id]);

  useEffect(() => {
    setAudioModeAsync({ playsInSilentMode: true, shouldPlayInBackground: true }).catch(() => undefined);
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  useEffect(() => {
    if (finished) return undefined;
    const timer = setInterval(load, POLL_MS);
    return () => clearInterval(timer);
  }, [finished, load]);

  // Anuncia a los lectores de pantalla cada bloque nuevo.
  useEffect(() => {
    const ready = detail?.podcast.readyBlocks ?? 0;
    if (ready > announced.current) {
      if (announced.current > 0 || ready === 1) {
        AccessibilityInfo.announceForAccessibility(ready === 1 ? 'Primer bloque listo. Ya puedes escuchar.' : `Bloque ${ready} listo`);
      }
      announced.current = ready;
    }
  }, [detail?.podcast.readyBlocks]);

  const blocks = detail?.blocks ?? [];
  const readyBlocks = blocks.filter((b) => b.status === 'READY' && b.audioUrl);

  const loadBlock = useCallback(
    (block: BlockView, seekSeconds = 0, autoplay = true) => {
      if (!block.audioUrl) return;
      player.replace({ uri: block.audioUrl });
      player.setPlaybackRate(rate);
      pendingSeek.current = seekSeconds > 0 ? seekSeconds : null;
      setCurrent(block.index);
      setWaitingNext(false);
      try {
        player.setActiveForLockScreen(true, { title: detail?.podcast.title ?? 'podcastAI', artist: 'podcastAI · Lía y Marco' });
      } catch {
        // No todas las plataformas tienen controles de pantalla bloqueada.
      }
      if (autoplay) player.play();
    },
    [player, rate, detail?.podcast.title],
  );

  // Aplica un salto pendiente cuando el audio ya está cargado.
  useEffect(() => {
    if (status.isLoaded && pendingSeek.current !== null) {
      const target = pendingSeek.current;
      pendingSeek.current = null;
      player.seekTo(target).catch(() => undefined);
    }
  }, [status.isLoaded, player]);

  /** Siguiente bloque en orden: si aún se está generando, espera a que llegue. */
  const goNext = useCallback(() => {
    if (current === null) return;
    const next = blocks.find((b) => b.index > current && b.status !== 'SKIPPED' && b.status !== 'FAILED');
    if (next && next.status === 'READY') {
      loadBlock(next);
    } else if (next && !finished) {
      setWaitingNext(true);
    }
  }, [blocks, current, finished, loadBlock]);

  useEffect(() => {
    if (status.didJustFinish) goNext();
  }, [status.didJustFinish]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (waitingNext) goNext();
  }, [detail, waitingNext]); // eslint-disable-line react-hooks/exhaustive-deps

  const elapsedBefore = useMemo(
    () => readyBlocks.filter((b) => current !== null && b.index < current).reduce((sum, b) => sum + b.durationMs, 0),
    [readyBlocks, current],
  );
  const totalMs = readyBlocks.reduce((sum, b) => sum + b.durationMs, 0);
  const positionMs = current === null ? 0 : elapsedBefore + status.currentTime * 1000;
  const currentMs = status.currentTime * 1000;

  const speaking = useMemo(() => {
    const block = readyBlocks.find((b) => b.index === current);
    const seg = block?.segments.find((x) => currentMs >= x.startMs && currentMs < x.endMs);
    return status.playing && seg ? seg.speaker : null;
  }, [readyBlocks, current, currentMs, status.playing]);

  function togglePlay() {
    if (current === null) {
      if (readyBlocks[0]) loadBlock(readyBlocks[0]);
    } else if (status.playing) {
      player.pause();
    } else {
      player.play();
    }
  }

  function skip(seconds: number) {
    if (current === null) return;
    const duration = status.duration || 0;
    const target = Math.min(Math.max(0, status.currentTime + seconds), Math.max(0, duration - 0.25));
    player.seekTo(target).catch(() => undefined);
  }

  function cycleRate() {
    const next = RATES[(RATES.indexOf(rate) + 1) % RATES.length];
    setRate(next);
    player.setPlaybackRate(next);
  }

  async function shareTranscript() {
    if (!detail) return;
    const text = [
      detail.podcast.title,
      '',
      ...readyBlocks.flatMap((b) => [`## ${b.title}`, ...b.segments.map((s) => `${s.speakerName}: ${s.text} (p. ${s.pages.join(', ')})`), '']),
    ].join('\n');
    if (Platform.OS === 'web') {
      const url = URL.createObjectURL(new Blob([text], { type: 'text/plain;charset=utf-8' }));
      const a = document.createElement('a');
      a.href = url;
      a.download = `${detail.podcast.title}.txt`;
      a.click();
      URL.revokeObjectURL(url);
    } else {
      await Share.share({ title: detail.podcast.title, message: text });
    }
  }

  /** Descarga el podcast entero en un MP3 (el navegador o el sistema lo guardan en Descargas). */
  function download() {
    if (!detail?.downloadUrl) return;
    if (Platform.OS === 'web') {
      const a = document.createElement('a');
      a.href = detail.downloadUrl;
      a.download = `${detail.podcast.title}.mp3`;
      a.click();
    } else {
      Linking.openURL(detail.downloadUrl);
    }
  }

  async function remove() {
    if (!confirmDelete) {
      setConfirmDelete(true);
      return;
    }
    player.pause();
    await api.remove(id).catch(() => undefined);
    router.replace('/');
  }

  if (!detail) {
    return (
      <Screen>
        {error ? <AppText tone="error" accessibilityRole="alert">{error}</AppText> : <ActivityIndicator color={theme.accent} accessibilityLabel="Cargando" />}
      </Screen>
    );
  }

  const p = detail.podcast;
  const generating = !finished;

  const playPause = (
    <Pressable
      onPress={togglePlay}
      accessibilityRole="button"
      accessibilityLabel={status.playing ? 'Pausar' : 'Reproducir'}
      style={[styles.play, { backgroundColor: theme.accent }]}
    >
      {status.playing ? (
        <Pause color={theme.onAccent} size={32} fill={theme.onAccent} />
      ) : (
        <Play color={theme.onAccent} size={32} fill={theme.onAccent} style={{ marginLeft: 4 }} />
      )}
    </Pressable>
  );

  return (
    <>
      <Stack.Screen options={{ title: modeLabel[p.mode] }} />
      <Screen>
        <View style={{ gap: spacing(1) }}>
          <AppText variant="title">{p.title}</AppText>
          <AppText tone="secondary">
            {detail.pageCount} páginas · {readyBlocks.length} de {p.totalBlocks || '…'} bloques
            {totalMs > 0 ? ` · ${spokenDuration(totalMs)}` : ''}
          </AppText>
        </View>

        <View style={styles.actions}>
          {detail.downloadUrl ? (
            <ActionButton
              label={finished ? 'Descargar MP3' : 'MP3 (hasta ahora)'}
              hint="Guarda el podcast en un archivo de audio"
              onPress={download}
              icon={<Download color={theme.text} size={20} strokeWidth={1.75} />}
            />
          ) : null}
          <ActionButton
            label={confirmDelete ? '¿Seguro? Pulsa otra vez' : 'Borrar'}
            onPress={remove}
            danger
            icon={<Trash2 color={theme.error} size={20} strokeWidth={1.75} />}
          />
        </View>

        <View style={[styles.tabs, { backgroundColor: theme.surface, borderColor: theme.border }]} accessibilityRole="tablist">
          {(['escuchar', 'guion'] as const).map((t) => {
            const selected = tab === t;
            return (
              <Pressable
                key={t}
                onPress={() => setTab(t)}
                accessibilityRole="tab"
                accessibilityState={{ selected }}
                style={[styles.tab, selected ? { backgroundColor: theme.accentSoft, borderColor: theme.accent } : { borderColor: 'transparent' }]}
              >
                <AppText variant="label" tone={selected ? 'accent' : 'secondary'}>
                  {t === 'escuchar' ? 'Escuchar' : 'Guion'}
                </AppText>
              </Pressable>
            );
          })}
        </View>

        {p.status === 'FAILED' ? (
          <Card style={{ backgroundColor: theme.errorSoft, borderColor: theme.errorSoft }}>
            <AppText tone="error" accessibilityRole="alert">
              {p.errorMessage ?? 'La generación ha fallado.'}
            </AppText>
          </Card>
        ) : null}

        {tab === 'escuchar' ? (
          <>
            {readyBlocks.length === 0 && generating ? <Progress detail={detail} /> : null}

            {readyBlocks.length > 0 ? (
              <Card style={{ gap: spacing(4), alignItems: 'center' }}>
                <View style={{ flexDirection: 'row', gap: spacing(6) }}>
                  {(['LIA', 'MARCO'] as const).map((who) => (
                    <View key={who} style={{ alignItems: 'center', gap: spacing(1) }}>
                      <PixelPortrait who={who} size={88} dim={speaking !== null && speaking !== who} />
                      <AppText variant="caption" tone={speaking === who ? 'accent' : 'secondary'}>
                        {speaking === who ? `> ${who === 'LIA' ? 'LÍA' : 'MARCO'}` : who === 'LIA' ? 'LÍA' : 'MARCO'}
                      </AppText>
                    </View>
                  ))}
                </View>
                {current === null ? (
                  <Button label="Escuchar ya" icon={<Play color={theme.onAccent} size={20} fill={theme.onAccent} />} onPress={togglePlay} style={{ alignSelf: 'stretch' }} />
                ) : (
                  <>
                    <AppText variant="caption" tone="secondary" accessibilityLabel={`Posición ${formatDuration(positionMs)} de ${formatDuration(totalMs)}`}>
                      {formatDuration(positionMs)} / {formatDuration(totalMs)}
                      {generating ? ' · generando más…' : ''}
                    </AppText>
                    <View
                      style={[styles.track, { backgroundColor: theme.background, borderColor: theme.border }]}
                      accessibilityRole="progressbar"
                      accessibilityValue={{ min: 0, max: Math.round(totalMs / 1000), now: Math.round(positionMs / 1000) }}
                    >
                      <View style={[styles.fill, { backgroundColor: theme.accent, width: `${totalMs > 0 ? Math.min(100, (positionMs / totalMs) * 100) : 0}%` }]} />
                    </View>
                    <View style={styles.controls}>
                      <RoundButton label="Retroceder 15 segundos" onPress={() => skip(-15)}>
                        <RotateCcw color={theme.text} size={26} strokeWidth={1.75} />
                      </RoundButton>
                      {playPause}
                      <RoundButton label="Avanzar 15 segundos" onPress={() => skip(15)}>
                        <RotateCw color={theme.text} size={26} strokeWidth={1.75} />
                      </RoundButton>
                    </View>
                    <Pressable
                      onPress={cycleRate}
                      accessibilityRole="button"
                      accessibilityLabel={`Velocidad ${rate} por. Cambiar velocidad`}
                      style={[styles.rate, { borderColor: theme.border }]}
                    >
                      <AppText variant="label">{rate.toString().replace('.', ',')}x</AppText>
                    </Pressable>
                    {waitingNext ? (
                      <AppText variant="caption" tone="secondary" accessibilityLiveRegion="polite">
                        Preparando el siguiente bloque…
                      </AppText>
                    ) : null}
                  </>
                )}
              </Card>
            ) : null}

            {blocks.some((b) => b.status === 'READY' || b.status === 'PENDING') ? (
              <View style={{ gap: spacing(1) }}>
                <AppText variant="heading">Capítulos</AppText>
                {blocks
                  .filter((b) => b.status === 'READY' || b.status === 'PENDING')
                  .map((block) => (
                    <Pressable
                      key={block.index}
                      onPress={() => block.status === 'READY' && loadBlock(block)}
                      disabled={block.status !== 'READY'}
                      accessibilityRole="button"
                      accessibilityLabel={`Capítulo ${block.index + 1}: ${block.title}${block.status === 'READY' ? '' : ', generándose'}`}
                      style={styles.chapter}
                    >
                      <AppText variant="label" tone={current === block.index ? 'accent' : 'primary'} style={{ flexShrink: 1 }}>
                        {block.index + 1}. {block.title}
                      </AppText>
                      <AppText variant="caption" tone="secondary">
                        {block.status === 'READY' ? formatDuration(block.durationMs) : 'generando…'}
                      </AppText>
                    </Pressable>
                  ))}
              </View>
            ) : null}

            {finished ? <QualityNote detail={detail} /> : null}
          </>
        ) : (
          <View style={{ gap: spacing(3) }}>
            <View style={{ flexDirection: 'row', alignItems: 'center', gap: spacing(3) }}>
              {readyBlocks.length > 0 ? (
                <Pressable
                  onPress={togglePlay}
                  accessibilityRole="button"
                  accessibilityLabel={status.playing ? 'Pausar' : 'Reproducir'}
                  style={[styles.miniPlay, { backgroundColor: theme.accent }]}
                >
                  {status.playing ? <Pause color={theme.onAccent} size={20} fill={theme.onAccent} /> : <Play color={theme.onAccent} size={20} fill={theme.onAccent} style={{ marginLeft: 2 }} />}
                </Pressable>
              ) : null}
              <AppText variant="caption" tone="secondary" style={{ flex: 1 }}>
                {readyBlocks.length > 0 ? 'Toca una frase para escucharla desde ahí.' : 'El guion aparecerá aquí en cuanto esté el primer bloque.'}
              </AppText>
              {readyBlocks.length > 0 ? (
                <Pressable onPress={shareTranscript} accessibilityRole="button" accessibilityLabel="Descargar o compartir el guion" style={styles.iconButton}>
                  <Share2 color={theme.text} size={22} strokeWidth={1.75} />
                </Pressable>
              ) : null}
            </View>
            {readyBlocks.map((block) => (
              <View key={block.index} style={{ gap: spacing(2) }}>
                <AppText variant="heading" tone={current === block.index ? 'accent' : 'primary'}>
                  {block.index + 1}. {block.title}
                </AppText>
                {block.segments.map((s) => {
                  const active = current === block.index && currentMs >= s.startMs && currentMs < s.endMs;
                  return (
                    <Pressable
                      key={s.position}
                      onPress={() => (current === block.index ? player.seekTo(s.startMs / 1000).catch(() => undefined) : loadBlock(block, s.startMs / 1000))}
                      accessibilityRole="button"
                      accessibilityLabel={`${s.speakerName}: ${s.text}. Página ${s.pages.join(', ')}`}
                      accessibilityHint="Reproduce desde esta frase"
                      accessibilityState={{ selected: active }}
                      style={[
                        styles.segment,
                        { backgroundColor: active ? theme.accentSoft : 'transparent', borderLeftColor: active ? theme.accent : 'transparent' },
                      ]}
                    >
                      <AppText variant="caption" tone={s.speaker === 'LIA' ? 'accent' : 'secondary'}>
                        {`${s.speakerName.toUpperCase()}>`}
                      </AppText>
                      <AppText>{s.text}</AppText>
                      {s.pages.length > 0 ? (
                        <AppText variant="caption" tone="secondary">
                          {s.pages.length === 1 ? 'Página' : 'Páginas'} {s.pages.join(', ')} del PDF
                        </AppText>
                      ) : null}
                    </Pressable>
                  );
                })}
              </View>
            ))}
          </View>
        )}
      </Screen>
    </>
  );
}

function ActionButton({ label, hint, onPress, icon, danger = false }: { label: string; hint?: string; onPress: () => void; icon: React.ReactNode; danger?: boolean }) {
  const theme = useTheme();
  return (
    <Pressable
      onPress={onPress}
      accessibilityRole="button"
      accessibilityLabel={label}
      accessibilityHint={hint}
      style={[styles.action, { borderColor: danger ? theme.error : theme.accent, backgroundColor: theme.background }]}
    >
      {icon}
      <AppText variant="label" tone={danger ? 'error' : 'primary'}>
        {label}
      </AppText>
    </Pressable>
  );
}

function RoundButton({ label, onPress, children }: { label: string; onPress: () => void; children: React.ReactNode }) {
  return (
    <Pressable onPress={onPress} accessibilityRole="button" accessibilityLabel={label} style={styles.round}>
      {children}
    </Pressable>
  );
}

/** Progreso por pasos reales mientras no hay audio. */
function Progress({ detail }: { detail: PodcastDetail }) {
  const theme = useTheme();
  const st = detail.podcast.status;
  const steps = [
    { label: 'Leyendo el PDF', done: true },
    { label: 'Preparando el mapa del contenido', done: st === 'GENERATING', active: st === 'QUEUED' || st === 'MAPPING' },
    { label: 'Escribiendo y verificando el primer bloque', done: detail.podcast.readyBlocks > 0, active: st === 'GENERATING' },
    { label: 'Poniendo voz a Lía y Marco', done: detail.podcast.readyBlocks > 0, active: false },
  ];
  return (
    <Card style={{ gap: spacing(3) }} accessibilityLiveRegion="polite">
      {steps.map((s) => (
        <View key={s.label} style={{ flexDirection: 'row', alignItems: 'center', gap: spacing(3) }}>
          <View style={{ width: 24, alignItems: 'center' }}>
            {s.done ? <Check color={theme.accent} size={20} /> : s.active ? <ActivityIndicator color={theme.accent} size="small" /> : <View style={[styles.dot, { borderColor: theme.border }]} />}
          </View>
          <AppText tone={s.done || s.active ? 'primary' : 'secondary'}>
            {s.label}
            {s.done ? ' (hecho)' : ''}
          </AppText>
        </View>
      ))}
      <AppText variant="caption" tone="secondary">
        Suele tardar menos de un minuto. Puedes salir y volver: seguirá generándose.
      </AppText>
    </Card>
  );
}

function QualityNote({ detail }: { detail: PodcastDetail }) {
  const q = detail.quality;
  const parts = [`${q.keptTurns} intervenciones comprobadas contra el PDF`];
  if (q.rewrittenTurns > 0) parts.push(`${q.rewrittenTurns} corregidas`);
  if (q.removedTurns > 0) parts.push(`${q.removedTurns} eliminadas por no tener respaldo`);
  if (q.firstAudioMs) parts.push(`primer audio en ${Math.round(q.firstAudioMs / 1000)} s`);
  return (
    <AppText variant="caption" tone="secondary">
      Verificación: {parts.join(', ')}.
    </AppText>
  );
}

const styles = StyleSheet.create({
  track: { height: 8, overflow: 'hidden', alignSelf: 'stretch', borderWidth: 1 },
  fill: { height: 6 },
  controls: { flexDirection: 'row', alignItems: 'center', gap: spacing(6) },
  play: { width: 72, height: 72, alignItems: 'center', justifyContent: 'center' },
  round: { width: 56, height: 56, alignItems: 'center', justifyContent: 'center' },
  rate: { minWidth: 72, minHeight: 48, borderRadius: radius, borderWidth: 1, alignItems: 'center', justifyContent: 'center', paddingHorizontal: spacing(3) },
  iconButton: { minWidth: 48, minHeight: 48, alignItems: 'center', justifyContent: 'center' },
  chapter: { minHeight: 48, flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', gap: spacing(2) },
  segment: { paddingVertical: spacing(2), paddingHorizontal: spacing(3), borderRadius: radius, borderLeftWidth: 3, gap: spacing(1) },
  actions: { flexDirection: 'row', flexWrap: 'wrap', gap: spacing(2) },
  action: { minHeight: 48, flexDirection: 'row', alignItems: 'center', gap: spacing(2), paddingHorizontal: spacing(4), borderRadius: radius, borderWidth: 1 },
  tabs: { flexDirection: 'row', borderWidth: 1, borderRadius: radius, padding: spacing(1), gap: spacing(1) },
  tab: { flex: 1, minHeight: 48, alignItems: 'center', justifyContent: 'center', borderRadius: radius, borderWidth: 1 },
  miniPlay: { width: 48, height: 48, alignItems: 'center', justifyContent: 'center' },
  dot: { width: 12, height: 12, borderWidth: 2 },
});
