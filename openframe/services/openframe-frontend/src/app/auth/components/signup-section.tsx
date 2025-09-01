'use client'

import { Button, Input, Label } from '@flamingo/ui-kit/components/ui'
import { useState } from 'react'
import { useDeployment } from '@app/hooks/use-deployment'
import { renderSvgIcon } from '@flamingo/ui-kit/components/icons'
import { AuthProvidersList } from '@flamingo/ui-kit/components/features'

interface RegisterRequest {
  tenantName: string
  tenantDomain: string
  firstName: string
  lastName: string
  email: string
  password: string
}

interface AuthSignupSectionProps {
  orgName: string
  domain: string
  onSubmit: (data: RegisterRequest) => void
  onSSO?: (provider: string) => void
  onBack: () => void
  isLoading: boolean
}

/**
 * Signup section for completing user registration
 */
export function AuthSignupSection({ orgName, domain, onSubmit, onSSO, onBack, isLoading }: AuthSignupSectionProps) {
  const { isCloud, isSelfHosted, isDevelopment, hostname } = useDeployment()
  
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [signupMethod, setSignupMethod] = useState<'form' | 'sso'>('form')
  
  // Dynamic domain suffix based on deployment
  const getDomainSuffix = () => {
    if (isCloud) {
      return '.openframe.ai'
    } else if (isSelfHosted) {
      // For self-hosted, use the actual hostname or allow custom domain
      return hostname.startsWith('localhost') ? '.local' : ''
    } else {
      // Development
      return '.dev'
    }
  }
  
  // Dynamic title and subtitle based on deployment
  const getTitle = () => {
    if (isCloud) {
      return 'Create Organization'
    } else {
      return 'Create Organization'
    }
  }
  
  const getSubtitle = () => {
    if (isCloud) {
      return 'Start your journey with OpenFrame'
    } else {
      return 'Start your journey with OpenFrame'
    }
  }
  
  // Dynamic button text based on deployment
  const getButtonText = () => {
    if (isCloud) {
      return 'Start Free Trial'
    } else {
      return 'Create Organization'
    }
  }

  const handleSubmit = () => {
    if (!firstName.trim() || !lastName.trim() || !email.trim() || !password || password !== confirmPassword) {
      return
    }

    const data: RegisterRequest = {
      tenantName: orgName,
      tenantDomain: domain,
      firstName: firstName.trim(),
      lastName: lastName.trim(),
      email: email.trim(),
      password
    }

    onSubmit(data)
  }

  const handleSSOClick = async (provider: string) => {
    setSignupMethod('sso')
    if (onSSO) {
      onSSO(provider)
    }
  }

  const isFormValid = firstName.trim() && lastName.trim() && email.trim() && 
                     password && confirmPassword && password === confirmPassword

  // SSO providers for cloud deployment
  const ssoProviders = [
    { provider: 'google', enabled: true },
    { provider: 'microsoft', enabled: true },
    { provider: 'github', enabled: true }
  ]

  return (
    <div className="w-full">
      <div className="w-full space-y-6 lg:space-y-10">
        
        {/* Complete Your Registration Section */}
        <div className="bg-ods-card border border-ods-border rounded-sm p-10">
          <div className="mb-6">
            <h1 className="font-heading text-[32px] font-semibold text-ods-text-primary leading-10 tracking-[-0.64px] mb-2">
              {getTitle()}
            </h1>
            <p className="font-body text-[18px] font-medium text-ods-text-secondary leading-6">
              {getSubtitle()}
            </p>
          </div>

          <div className="space-y-6"
            onClick={() => setSignupMethod('form')}>
            
            {/* Organization details (disabled) */}
            <div className="flex flex-col md:flex-row gap-6">
              <div className="flex-1 flex flex-col gap-1">
                <Label>{isSelfHosted ? 'Instance Name' : 'Organization Name'}</Label>
                <Input
                  value={orgName}
                  disabled
                  className="bg-ods-card border-ods-border text-ods-text-secondary font-body text-[18px] font-medium leading-6 p-3"
                />
              </div>
              <div className="flex-1 flex flex-col gap-1">
                <Label>{isSelfHosted ? 'Instance URL' : 'Domain'}</Label>
                <div className="relative">
                  <Input
                    value={domain}
                    disabled={!isSelfHosted || isLoading}
                    className="bg-ods-card border-ods-border text-ods-text-secondary font-body text-[18px] font-medium leading-6 p-3 pr-32"
                  />
                  {getDomainSuffix() && (
                    <span className="absolute right-3 top-1/2 -translate-y-1/2 text-ods-text-secondary font-body text-[14px] font-medium leading-5">
                      {getDomainSuffix()}
                    </span>
                  )}
                </div>
              </div>
            </div>

            {isCloud && onSSO && (
              <div className="space-y-6 mb-8">
                <AuthProvidersList
                  enabledProviders={ssoProviders}
                  onProviderClick={handleSSOClick}
                  loading={isLoading && signupMethod === 'sso'}
                  orientation="vertical"
                  showDivider={false}
                />
              
                {/* Divider with OR */}
                <div className="relative">
                  <div className="absolute inset-0 flex items-center">
                    <div className="w-full border-t border-ods-border"></div>
                  </div>
                  <div className="relative flex justify-center text-sm">
                    <span className="bg-ods-card px-4 text-ods-text-secondary">
                      or register manually
                    </span>
                  </div>
                </div>
              </div>
            )}

            {/* Personal details */}
            <div className="flex flex-col md:flex-row gap-6">
              <div className="flex-1 flex flex-col gap-1">
                <Label>First Name</Label>
                <Input
                  value={firstName}
                  onChange={(e) => setFirstName(e.target.value)}
                  placeholder="Your First Name"
                  disabled={isLoading}
                  className="bg-ods-card border-ods-border text-ods-text-secondary font-body text-[18px] font-medium leading-6 placeholder:text-ods-text-secondary p-3"
                />
              </div>
              <div className="flex-1 flex flex-col gap-1">
                <Label>Last Name</Label>
                <Input
                  value={lastName}
                  onChange={(e) => setLastName(e.target.value)}
                  placeholder="Your Last Name"
                  disabled={isLoading}
                  className="bg-ods-card border-ods-border text-ods-text-secondary font-body text-[18px] font-medium leading-6 placeholder:text-ods-text-secondary p-3"
                />
              </div>
            </div>

            <div className="flex flex-col gap-1">
              <Label>Email</Label>
              <Input
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="username@mail.com"
                disabled={isLoading}
                className="bg-ods-card border-ods-border text-ods-text-secondary font-body text-[18px] font-medium leading-6 placeholder:text-ods-text-secondary p-3"
              />
            </div>

            <div className="flex flex-col md:flex-row gap-6">
              <div className="flex-1 flex flex-col gap-1">
                <Label>Password</Label>
                <Input
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder={"Choose a Strong Password"}
                  disabled={isLoading}
                  className="bg-ods-card border-ods-border text-ods-text-secondary font-body text-[18px] font-medium leading-6 placeholder:text-ods-text-secondary p-3"
                />
                {isCloud && password && password.length < 8 && (
                  <p className="text-xs text-red-500 mt-1">Password must be at least 8 characters</p>
                )}
              </div>
              <div className="flex-1 flex flex-col gap-1">
                <Label>Confirm Password</Label>
                <Input
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  placeholder="Confirm your Password"
                  disabled={isLoading}
                  className="bg-ods-card border-ods-border text-ods-text-secondary font-body text-[18px] font-medium leading-6 placeholder:text-ods-text-secondary p-3"
                />
                {confirmPassword && password !== confirmPassword && (
                  <p className="text-xs text-red-500 mt-1">Passwords do not match</p>
                )}
              </div>
            </div>

            <div className="flex flex-col sm:flex-row gap-4 sm:gap-6 items-stretch sm:items-center">
              <Button
                onClick={onBack}
                disabled={isLoading}
                variant="outline"
                className="w-full sm:flex-1"
              >
                Back
              </Button>
              <Button
                onClick={handleSubmit}
                disabled={!isFormValid || isLoading}
                loading={isLoading}
                variant="primary"
                className="w-full sm:flex-1"
              >
                {getButtonText()}
              </Button>
            </div>
          </div>
        </div>

      </div>
    </div>
  )
}