import { useEffect, useState, useSyncExternalStore } from 'react'
import type { PropsWithChildren } from 'react'
import { QueryClientProvider } from '@tanstack/react-query'
import type { QueryClient } from '@tanstack/react-query'
import { createQueryClient } from '../../lib/query-client'
import { SessionController } from './session'
import { AuthContext } from './auth-context'

export function AuthProvider({ children, client }: PropsWithChildren<{ client?: QueryClient }>) {
  const [controller] = useState(() => new SessionController(client ?? createQueryClient()))
  const state = useSyncExternalStore(controller.subscribe, controller.getSnapshot)
  useEffect(() => {
    void controller.refresh()
    const refresh = () => { if (document.visibilityState === 'visible') void controller.refresh() }
    window.addEventListener('focus', refresh)
    return () => { window.removeEventListener('focus', refresh); controller.dispose() }
  }, [controller])
  return <QueryClientProvider client={controller.client}>
    <AuthContext value={{ ...state, login: (email, password) => controller.login(email, password), enterDemo: () => controller.enterDemo(), logout: () => controller.logout(), refresh: () => controller.refresh() }}>
      {children}
    </AuthContext>
  </QueryClientProvider>
}
