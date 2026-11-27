import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react'
import { configureClient } from '../api/client.js'
import { authApi, policyApi } from '../api/index.js'

const AuthContext = createContext(null)

/**
 * Session state. The JWT is held in memory only (frontend/SPEC.md §6.2, CLAUDE.md §10.10):
 * never in localStorage/sessionStorage, so a page reload requires signing in again.
 * Also loads the client config (API-037) once per session.
 */
export function AuthProvider({ children, onSessionEnded }) {
  const [session, setSession] = useState({ token: null, user: null, config: null })
  const tokenRef = useRef(null)
  const endedRef = useRef(onSessionEnded)
  useEffect(() => {
    endedRef.current = onSessionEnded
  })

  const logout = useCallback(() => {
    tokenRef.current = null
    setSession({ token: null, user: null, config: null })
  }, [])

  useEffect(() => {
    configureClient({
      getToken: () => tokenRef.current,
      onUnauthorized: () => {
        if (!tokenRef.current) return
        tokenRef.current = null
        setSession({ token: null, user: null, config: null })
        endedRef.current?.()
      },
    })
  }, [])

  const login = useCallback(async (email, password) => {
    const res = await authApi.login(email, password)
    tokenRef.current = res.accessToken
    try {
      const [user, config] = await Promise.all([authApi.me(), policyApi.clientConfig()])
      setSession({ token: res.accessToken, user, config })
      return user
    } catch (err) {
      tokenRef.current = null
      throw err
    }
  }, [])

  const refreshUser = useCallback(async () => {
    const user = await authApi.me()
    setSession((s) => ({ ...s, user }))
  }, [])

  const value = useMemo(() => {
    const { user } = session
    return {
      user,
      config: session.config,
      isAuthenticated: Boolean(session.token && user),
      login,
      logout,
      refreshUser,
      hasRole: (...roles) => Boolean(user && roles.includes(user.role)),
      can: (capability) => Boolean(user?.[capability]),
    }
  }, [session, login, logout, refreshUser])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside <AuthProvider>')
  return ctx
}

/** For tests and stories: provide a fixed auth value. */
export function MockAuthProvider({ value, children }) {
  const full = {
    isAuthenticated: true,
    login: async () => {},
    logout: () => {},
    refreshUser: async () => {},
    config: {
      receiptMandatoryAbove: '500.00',
      tier1Cities: ['Kolkata', 'Mumbai', 'Delhi', 'Chennai', 'Bangalore'],
      categories: ['MEALS', 'HOTEL', 'LOCAL_CAB'],
      allowedReceiptTypes: ['application/pdf', 'image/jpeg', 'image/png'],
      maxReceiptBytes: 5242880,
    },
    hasRole: (...roles) => roles.includes(value.user?.role),
    can: (c) => Boolean(value.user?.[c]),
    ...value,
  }
  return <AuthContext.Provider value={full}>{children}</AuthContext.Provider>
}
