import { Platform } from 'react-native';
import { getDeviceId } from './device';

export type Mode = 'RESUMEN' | 'A_FONDO';
export type PodcastStatus = 'QUEUED' | 'MAPPING' | 'GENERATING' | 'READY' | 'FAILED';

export interface PodcastSummary {
  id: string;
  title: string;
  mode: Mode;
  language: string;
  status: PodcastStatus;
  totalBlocks: number;
  readyBlocks: number;
  durationMs: number;
  errorMessage: string | null;
  createdAt: string;
}

export interface SegmentView {
  position: number;
  speaker: 'LIA' | 'MARCO';
  speakerName: string;
  text: string;
  pages: number[];
  startMs: number;
  endMs: number;
}

export interface BlockView {
  index: number;
  title: string;
  status: 'PENDING' | 'READY' | 'SKIPPED' | 'FAILED';
  durationMs: number;
  audioUrl: string | null;
  segments: SegmentView[];
}

export interface PodcastDetail {
  podcast: PodcastSummary;
  pageCount: number;
  quality: { keptTurns: number; removedTurns: number; rewrittenTurns: number; firstAudioMs: number | null };
  blocks: BlockView[];
}

export interface AccountView {
  plan: 'FREE' | 'PREMIUM';
  minutesUsed: number;
  monthlyMinutes: number;
  freePodcastsLeft: number;
  periodStart: string;
  scriptEngine: string;
  voiceEngine: string;
}

export interface PickedPdf {
  name: string;
  uri: string;
  size?: number;
  file?: File;
}

function defaultBaseUrl(): string {
  if (Platform.OS === 'web' && typeof window !== 'undefined') {
    return `${window.location.protocol}//${window.location.hostname}:8080`;
  }
  return 'http://localhost:8080';
}

export const API_URL = (process.env.EXPO_PUBLIC_API_URL || defaultBaseUrl()).replace(/\/+$/, '');

export class ApiError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const deviceId = await getDeviceId();
  let response: Response;
  try {
    response = await fetch(`${API_URL}${path}`, {
      ...init,
      headers: { 'X-Device-Id': deviceId, ...(init.headers ?? {}) },
    });
  } catch {
    throw new ApiError(0, 'No hay conexión con el servidor.');
  }
  if (!response.ok) {
    let message = 'Algo ha fallado. Inténtalo de nuevo.';
    try {
      const body = await response.json();
      if (body?.error) message = body.error;
    } catch {
      // respuesta sin JSON
    }
    throw new ApiError(response.status, message);
  }
  return (response.status === 204 ? undefined : await response.json()) as T;
}

export const api = {
  me: () => request<AccountView>('/api/me'),
  list: () => request<PodcastSummary[]>('/api/podcasts'),
  detail: (id: string) => request<PodcastDetail>(`/api/podcasts/${id}`),
  remove: (id: string) => request<void>(`/api/podcasts/${id}`, { method: 'DELETE' }),
  deleteAccount: () => request<void>('/api/me', { method: 'DELETE' }),
  create: (pdf: PickedPdf, mode: Mode, language: string) => {
    const form = new FormData();
    if (pdf.file) {
      form.append('file', pdf.file, pdf.name);
    } else {
      // React Native: el archivo se pasa por su URI.
      form.append('file', { uri: pdf.uri, name: pdf.name, type: 'application/pdf' } as unknown as Blob);
    }
    form.append('mode', mode);
    form.append('language', language);
    return request<PodcastSummary>('/api/podcasts', { method: 'POST', body: form });
  },
};
