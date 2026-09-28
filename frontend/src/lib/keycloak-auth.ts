import {
  UserManager,
  WebStorageStateStore,
  type User as OidcUser,
} from 'oidc-client-ts'
import {
  claimsToUser,
  type AuthClient,
  type AuthSession,
  type KeycloakClaims,
} from '@/core/auth/oidc'

/**
 * Web adapter for the {@link AuthClient} port, backed by Keycloak via
 * oidc-client-ts (Authorization Code + PKCE). All redirect/DOM mechanics live
 * here so `src/core` stays platform-agnostic.
 */

const env = import.meta.env
const authority = `${env.VITE_KEYCLOAK_URL}/realms/${env.VITE_KEYCLOAK_REALM}`

/** Decode a JWT payload (base64url, UTF-8) without verifying the signature. */
function decodeJwt(token: string): KeycloakClaims {
  try {
    const b64 = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const bin = atob(b64)
    const bytes = Uint8Array.from(bin, (c) => c.charCodeAt(0))
    return JSON.parse(new TextDecoder().decode(bytes)) as KeycloakClaims
  } catch {
    return {}
  }
}

/**
 * Roles live in the *access* token's `realm_access` claim; username/email come
 * from the id-token profile (or the access token as a fallback).
 */
function toSession(u: OidcUser | null): AuthSession | null {
  if (!u || u.expired || !u.access_token) return null
  const at = decodeJwt(u.access_token)
  const claims: KeycloakClaims = {
    sub: at.sub ?? u.profile.sub,
    preferred_username: at.preferred_username ?? u.profile.preferred_username,
    email: at.email ?? u.profile.email,
    realm_access: at.realm_access,
  }
  return { user: claimsToUser(claims), token: u.access_token }
}

export function createKeycloakAuth(): AuthClient {
  const manager = new UserManager({
    authority,
    client_id: env.VITE_KEYCLOAK_CLIENT_ID,
    redirect_uri: `${window.location.origin}/auth/callback`,
    post_logout_redirect_uri: window.location.origin,
    response_type: 'code',
    scope: 'openid profile email',
    automaticSilentRenew: true,
    userStore: new WebStorageStateStore({ store: window.localStorage }),
  })

  return {
    async getSession() {
      return toSession(await manager.getUser())
    },
    async signinRedirect() {
      await manager.signinRedirect()
    },
    async completeSignin() {
      return toSession(await manager.signinRedirectCallback())
    },
    async signout() {
      // RP-initiated logout: clear the local session, then redirect to
      // Keycloak's end-session endpoint so the SSO cookie is terminated too.
      // Without this, Keycloak keeps its SSO session and the next sign-in
      // skips the login form (silent re-auth) — landing straight in the app.
      const user = await manager.getUser()
      await manager.removeUser()
      const endSession = new URL(`${authority}/protocol/openid-connect/logout`)
      endSession.searchParams.set('post_logout_redirect_uri', window.location.origin)
      if (user?.id_token) {
        endSession.searchParams.set('id_token_hint', user.id_token)
      } else {
        endSession.searchParams.set('client_id', env.VITE_KEYCLOAK_CLIENT_ID)
      }
      window.location.assign(endSession.toString())
    },
    subscribe(listener) {
      const onLoaded = (u: OidcUser) => listener(toSession(u))
      const onGone = () => listener(null)
      manager.events.addUserLoaded(onLoaded)
      manager.events.addUserUnloaded(onGone)
      manager.events.addAccessTokenExpired(onGone)
      manager.events.addSilentRenewError(onGone)
      return () => {
        manager.events.removeUserLoaded(onLoaded)
        manager.events.removeUserUnloaded(onGone)
        manager.events.removeAccessTokenExpired(onGone)
        manager.events.removeSilentRenewError(onGone)
      }
    },
  }
}