import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useFonts } from 'expo-font';
import { VT323_400Regular } from '@expo-google-fonts/vt323';
import { PressStart2P_400Regular } from '@expo-google-fonts/press-start-2p';
import { fonts, useTheme } from '../lib/theme';

export default function RootLayout() {
  const theme = useTheme();
  const [loaded] = useFonts({ VT323_400Regular, PressStart2P_400Regular });

  if (!loaded) {
    return <View style={{ flex: 1, backgroundColor: theme.background }} />;
  }

  return (
    <SafeAreaProvider>
      <StatusBar style="light" />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: theme.background },
          headerTintColor: theme.text,
          headerTitleStyle: { fontFamily: fonts.pixel, fontSize: 12 },
          headerShadowVisible: false,
          contentStyle: { backgroundColor: theme.background },
        }}
      >
        <Stack.Screen name="index" options={{ title: 'podcastAI' }} />
        <Stack.Screen name="nuevo" options={{ title: 'Nuevo podcast' }} />
        <Stack.Screen name="podcast/[id]" options={{ title: 'Podcast' }} />
        <Stack.Screen name="cuenta" options={{ title: 'Cuenta y plan' }} />
      </Stack>
    </SafeAreaProvider>
  );
}
