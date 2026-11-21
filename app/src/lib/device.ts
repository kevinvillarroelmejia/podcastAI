import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';

const KEY = 'podcastai.deviceId';
let cached: string | null = null;

/**
 * Id anónimo del dispositivo. En móvil se guarda en el llavero del sistema (expo-secure-store),
 * nunca en AsyncStorage. En la fase 2 se sustituye por la sesión de Supabase Auth.
 */
export async function getDeviceId(): Promise<string> {
  if (cached) return cached;
  let id: string | null = null;
  if (Platform.OS === 'web') {
    try {
      id = window.localStorage.getItem(KEY);
    } catch {
      id = null;
    }
  } else {
    id = await SecureStore.getItemAsync(KEY);
  }
  if (!id) {
    id = Crypto.randomUUID();
    if (Platform.OS === 'web') {
      try {
        window.localStorage.setItem(KEY, id);
      } catch {
        // Navegación privada: el id dura lo que la pestaña.
      }
    } else {
      await SecureStore.setItemAsync(KEY, id);
    }
  }
  cached = id;
  return id;
}

export async function resetDeviceId(): Promise<void> {
  cached = null;
  if (Platform.OS === 'web') {
    try {
      window.localStorage.removeItem(KEY);
    } catch {
      // nada que borrar
    }
  } else {
    await SecureStore.deleteItemAsync(KEY);
  }
}
