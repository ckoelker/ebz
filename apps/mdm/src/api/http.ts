import axios, { type AxiosRequestConfig, type AxiosError } from 'axios';
import { getAccessToken } from '@/auth';

// Orval-Custom-Mutator (axios-functions): EINE axios-Instanz für alle generierten Endpunkte.
// Cockpit läuft same-origin (Vite-Proxy / nginx leiten /bildung + /q an Quarkus weiter) → baseURL ''
// (relativ), kein CORS. Der Interceptor hängt das OIDC-access_token an; Liste (GET) bleibt anonym,
// Schreiben braucht die Rolle katalog-pflege.
const instance = axios.create({ baseURL: '' });

instance.interceptors.request.use(async (config) => {
  const token = await getAccessToken();
  if (token) {
    config.headers = config.headers ?? {};
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export const http = <T>(config: AxiosRequestConfig): Promise<T> =>
  instance.request<T>(config).then((r) => r.data);

// Fehlertyp für die generierten Funktionen (orval override.errorType).
export type ErrorType<E> = AxiosError<E>;
