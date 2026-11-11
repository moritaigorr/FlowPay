import { QueryClient } from '@tanstack/react-query'
import { ApiError } from './http'

export function retryRead(failureCount: number, error: Error) {
  if (error.name === 'AbortError') return false
  if (error instanceof ApiError && error.status < 500) return false
  return failureCount < 2
}

export function createQueryClient() {
  return new QueryClient({ defaultOptions: {
    queries: { retry: retryRead, retryDelay: (attempt) => Math.min(500 * 2 ** attempt, 2000), staleTime: 30_000, refetchOnWindowFocus: true },
    mutations: { retry: false },
  } })
}

// The lifecycle generation also separates two logins to the same account.
export function sessionKey(scope: string, ...parts: readonly unknown[]) {
  return ['session', scope, ...parts] as const
}
