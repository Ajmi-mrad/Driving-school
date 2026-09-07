import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './i18n'
import App from './App.tsx'
import { AuthProvider } from '@/core/auth/AuthContext'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import { DirectionManager } from '@/components/DirectionManager'
import { createKeycloakAuth } from '@/lib/keycloak-auth'

const authClient = createKeycloakAuth()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider authClient={authClient}>
      <TooltipProvider>
        <DirectionManager />
        <App />
        <Toaster richColors position="top-right" />
      </TooltipProvider>
    </AuthProvider>
  </StrictMode>,
)
