import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode, useEffect, useState, type ReactNode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import i18n, { directionOf } from '@/i18n'
import { AuthProvider } from '@/auth/AuthProvider'
import { DirectionProvider } from '@/components/ui/direction'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'
import { ApiError } from '@/lib/api'
import App from './App.tsx'
import './index.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // client errors (402/403/404…) are answers, not glitches: don't retry them
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
      refetchOnWindowFocus: false,
    },
  },
})

/** Radix components (menus, dialogs, sidebar) follow the active language's direction. */
function Directional({ children }: { children: ReactNode }) {
  const [dir, setDir] = useState(directionOf(i18n.language))
  useEffect(() => {
    const update = (language: string) => setDir(directionOf(language))
    i18n.on('languageChanged', update)
    return () => i18n.off('languageChanged', update)
  }, [])
  return (
    <DirectionProvider dir={dir}>
      <TooltipProvider>{children}</TooltipProvider>
      <Toaster position={dir === 'rtl' ? 'top-left' : 'top-right'} richColors />
    </DirectionProvider>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <Directional>
        <BrowserRouter>
          <AuthProvider>
            <App />
          </AuthProvider>
        </BrowserRouter>
      </Directional>
    </QueryClientProvider>
  </StrictMode>,
)
