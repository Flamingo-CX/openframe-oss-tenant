'use client'

import { AuthLoginSection } from '@app/auth/components/login-section'
import { AuthLayout } from '@app/auth/layouts'
import { useAuth } from '@app/auth/hooks/use-auth'
import { useRouter } from 'next/navigation'
import { useEffect } from 'react'

export default function LoginPage() {
  const router = useRouter()
  const { 
    email, 
    tenantInfo, 
    hasDiscoveredTenants,
    discoveryAttempted, 
    availableProviders, 
    isLoading, 
    isInitialized,
    loginWithSSO,
    discoverTenants 
  } = useAuth()

  // Auto-discover tenants if email exists but discovery hasn't been attempted yet
  useEffect(() => {
    if (!isInitialized) return // Wait for localStorage to initialize
    
    // Only attempt discovery once - when we have an email and haven't attempted discovery yet
    if (email && !discoveryAttempted && !isLoading) {
      discoverTenants(email)
    } else if (!email && !isLoading) {
      router.push('/auth')
    }
  }, [email, discoveryAttempted, isLoading, isInitialized, discoverTenants, router])

  const handleSSO = async (provider: string) => {
    await loginWithSSO(provider)
  }

  const handleBack = () => {
    router.push('/auth/')
  }


  return (
    <AuthLayout>
      <AuthLoginSection
        email={email}
        tenantInfo={tenantInfo}
        hasDiscoveredTenants={hasDiscoveredTenants}
        availableProviders={availableProviders}
        onSSO={handleSSO}
        onBack={handleBack}
        isLoading={isLoading}
      />
    </AuthLayout>
  )
}