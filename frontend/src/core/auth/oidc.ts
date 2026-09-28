/**
 * Auth port — framework- and platform-agnostic (no DOM, no redirect API).
 *
 * The web app injects a Keycloak (OIDC / PKCE) adapter; a future React
 * Native app injects its own native-OIDC adapter. `AuthContext` depends only
 * on this interface, mirroring the `AuthStorage` port pattern.
 */
import { ROLES, type Role, type User } from '../types'

export interface AuthSession {
  user: User
  token: string
}

export interface AuthClient {
  /** Current session if one is stored and unexpired, else null. */
  getSession(): Promise<AuthSession | null>
  /** Begin login (redirect to the identity provider). */
  signinRedirect(): Promise<void>
  /** Complete login on the callback route; returns the new session. */
  completeSignin(): Promise<AuthSession | null>
  /** Clear the local session (and optionally sign out at the IdP). */
  signout(): Promise<void>
  /** React to session changes (silent renew, token expiry). Returns unsubscribe. */
  subscribe(listener: (session: AuthSession | null) => void): () => void
}

/** Subset of Keycloak JWT claims the frontend consumes. */
export interface KeycloakClaims {
  sub?: string
  preferred_username?: string
  email?: string
  realm_access?: { roles?: string[] }
}

/**
 * Build a slim {@link User} from Keycloak claims. Profile fields not present
 * in the token (firstName/lastName/phones/permitNumber) stay empty until a
 * dedicated profile endpoint exists.
 */
export function claimsToUser(claims: KeycloakClaims): User {
  const roles = (claims.realm_access?.roles ?? []).filter((r): r is Role =>
    (ROLES as readonly string[]).includes(r),
  )
  return {
    id: claims.sub ?? '',
    username: claims.preferred_username ?? '',
    email: claims.email ?? '',
    firstName: '',
    lastName: '',
    phones: [],
    roles,
    permitNumber: null,
    active: true,
    notificationsEnabled: true,
  }
}