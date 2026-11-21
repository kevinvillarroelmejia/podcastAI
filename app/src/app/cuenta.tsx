import { useCallback, useState } from 'react';
import { View } from 'react-native';
import { router, useFocusEffect } from 'expo-router';
import { api, ApiError, API_URL, type AccountView } from '../lib/api';
import { resetDeviceId } from '../lib/device';
import { spacing, useTheme } from '../lib/theme';
import { AppText } from '../components/AppText';
import { Button } from '../components/Button';
import { Card } from '../components/Card';
import { Screen } from '../components/Screen';

/** Cuenta y plan: minutos usados, plan y borrar mis datos. */
export default function AccountScreen() {
  const theme = useTheme();
  const [account, setAccount] = useState<AccountView | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirm, setConfirm] = useState(false);

  useFocusEffect(
    useCallback(() => {
      api.me().then(setAccount).catch((e) => setError(e instanceof ApiError ? e.message : 'Sin conexión.'));
    }, []),
  );

  async function deleteEverything() {
    if (!confirm) {
      setConfirm(true);
      return;
    }
    try {
      await api.deleteAccount();
      await resetDeviceId();
      router.replace('/');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'No se ha podido borrar.');
    }
  }

  const premium = account?.plan === 'PREMIUM';
  const used = account ? Math.min(account.minutesUsed, account.monthlyMinutes) : 0;
  const testMode = account && (account.scriptEngine === 'mock' || account.voiceEngine === 'mock');

  return (
    <Screen>
      {error ? <AppText tone="error" accessibilityRole="alert">{error}</AppText> : null}

      {account ? (
        <Card style={{ gap: spacing(3) }}>
          <AppText variant="heading">{premium ? 'Plan Premium' : 'Plan gratis'}</AppText>
          {premium ? (
            <>
              <AppText>
                {Math.round(used)} de {account.monthlyMinutes} minutos usados este mes
              </AppText>
              <View
                style={{ height: 8, borderRadius: 4, backgroundColor: theme.accentSoft, overflow: 'hidden' }}
                accessibilityRole="progressbar"
                accessibilityValue={{ min: 0, max: account.monthlyMinutes, now: Math.round(used) }}
              >
                <View style={{ height: 8, backgroundColor: theme.accent, width: `${(used / account.monthlyMinutes) * 100}%` }} />
              </View>
              <AppText variant="caption" tone="secondary">
                Los minutos se renuevan cada mes desde el {new Date(account.periodStart).toLocaleDateString()}.
              </AppText>
            </>
          ) : (
            <>
              <AppText>
                {account.freePodcastsLeft > 0 ? 'Te queda 1 resumen gratis.' : 'Ya has usado tu resumen gratis.'}
              </AppText>
              <AppText tone="secondary">
                Premium: 10 €/mes con 120 minutos al mes (unos 15 resúmenes) y el modo A fondo.
              </AppText>
              <Button label="Hazte Premium (pronto en las tiendas)" disabled onPress={() => undefined} />
            </>
          )}
        </Card>
      ) : null}

      {testMode ? (
        <Card style={{ gap: spacing(2) }}>
          <AppText variant="label">Modo de prueba</AppText>
          <AppText tone="secondary">
            El servidor no tiene {account?.scriptEngine === 'mock' ? 'clave de Claude' : ''}
            {account?.scriptEngine === 'mock' && account?.voiceEngine === 'mock' ? ' ni ' : ''}
            {account?.voiceEngine === 'mock' ? 'clave de voz de OpenAI' : ''}: el guion usa frases literales del PDF y el audio es
            silencio. Añade las claves en el backend para oír a Lía y Marco.
          </AppText>
        </Card>
      ) : null}

      <Card style={{ gap: spacing(3) }}>
        <AppText variant="heading">Privacidad</AppText>
        <AppText tone="secondary">
          Tu PDF no se guarda: solo su texto, que se borra a los 30 días. Los audios son privados y se quedan hasta que los borres.
        </AppText>
        <Button label={confirm ? 'Pulsa otra vez: se borrará todo' : 'Borrar mis datos'} variant="danger" onPress={deleteEverything} />
      </Card>

      <AppText variant="caption" tone="secondary">
        Servidor: {API_URL}
      </AppText>
    </Screen>
  );
}
