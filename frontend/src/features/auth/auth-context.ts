import { createContext, useContext } from 'react'
import type { AuthState, SessionController } from './session'

export interface AuthContextValue extends AuthState {
  login: SessionController['login']
  enterDemo: SessionController['enterDemo']
  logout: SessionController['logout']
  refresh: SessionController['refresh']
}
export const AuthContext = createContext<AuthContextValue | null>(null)
export function useAuth() {
  const auth = useContext(AuthContext)
  if (!auth) throw new Error('useAuth requires AuthProvider')
  return auth
}
