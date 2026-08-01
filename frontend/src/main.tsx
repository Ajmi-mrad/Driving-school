import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import './i18n'
import App from './App.tsx'
import { AuthProvider } from '@/core/auth/AuthContext'
import { TooltipProvider } from '@/components/ui/tooltip'
import { Toaster } from '@/components/ui/sonner'
import { DirectionManager } from '@/components/DirectionManager'
import { webStorage } from '@/lib/web-storage'

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider storage={webStorage}>
      <TooltipProvider>
        <DirectionManager />
        <App />
        <Toaster richColors position="top-right" />
      </TooltipProvider>
    </AuthProvider>
  </StrictMode>,
)
