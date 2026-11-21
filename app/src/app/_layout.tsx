import { Stack } from 'expo-router';
import { StatusBar } from 'expo-status-bar';
import { useColorScheme, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useFonts, Inter_400Regular, Inter_500Medium, Inter_600SemiBold, Inter_700Bold } from '@expo-google-fonts/inter';
import { fonts, useTheme } from '../lib/theme';

export default function RootLayout() {
  const theme = useTheme();
  const scheme = useColorScheme();
  const [loaded] = useFonts({ Inter_400Regular, Inter_500Medium, Inter_600SemiBold, Inter_700Bold });

  if (!loaded) {
    return <View style={{ flex: 1, backgroundColor: theme.background }} />;
  }

  return (
    <SafeAreaProvider>
      <StatusBar style={scheme === 'dark' ? 'light' : 'dark'} />
      <Stack
        screenOptions={{
          headerStyle: { backgroundColor: theme.background },
          headerTintColor: theme.text,
          headerTitleStyle: { fontFamily: fonts.semibold, fontSize: 18 },
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
