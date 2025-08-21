import React from 'react'
import { AppConfig } from '../app-config'
import { OpenFrameLogo, UserIcon, HamburgerIcon, IconsXIcon as XIcon } from '@flamingo/ui-kit/components/icons'
import { Button } from '@flamingo/ui-kit/components/ui'

export const openframeConfig: AppConfig = {
  name: 'OpenFrame',
  legalName: 'Flamingo AI, Inc.',
  description: 'Open-source application framework and development platform. Build scalable applications with modern tools and patterns.',
  url: 'https://openframe.dev',
  logo: '/logo.png',
  slogan: 'Open Source Application Framework',
  platform: 'openframe',
  brandColors: {
    primary: 'var(--ods-accent)',      // OpenFrame cyan from ODS tokens
    accent: 'var(--ods-text-primary)', // Primary text color from ODS
    background: 'var(--ods-system-greys-black)',       // Background from ODS tokens
    text: 'var(--ods-text-primary)'    // Text from ODS tokens
  },
  seo: {
    title: 'OpenFrame - Open Source Framework',
    titleTemplate: '%s | OpenFrame',
    description: 'Modern open-source application framework for building scalable web applications. Developer-friendly tools and patterns.',
    keywords: ['open source', 'framework', 'web development', 'application development', 'developer tools'],
    ogImage: '/assets/openframe/og-image.png'
  },
  layout: {
    showHeader: true,
    showFooter: true,
    showAnnouncement: false,
    showSidebar: false,
    headerType: 'platform'
  },
  navigation: {
    logo: {
      href: '/',
      text: 'OpenFrame',
      icon: 'openframe',
      getElement: () => (
        <span className="flex items-center gap-3">
          <OpenFrameLogo className="h-8 w-8" />
          <span className="font-heading text-heading-5 font-semibold text-ods-text-primary">
            OpenFrame
          </span>
        </span>
      )
    },
    showPlatformNav: true,
    showAdminNav: false,
    showAdminMenuInHeader: false,
    allowedRoutes: ['/docs', '/examples', '/api', '/profile', '/contact'],
    restrictedRoutes: ['/admin', '/vendors', '/margin-increase']
  },
  ui: {
    showUserMenu: true,
    showMobileNav: true,
    showSearchBar: true,
    headerStyle: 'default',
    headerAutoHide: true,
    getHeaderActions: ({ user, router, pathname, onSignUp }) => {
      const left: React.ReactElement[] = []
      const right: React.ReactElement[] = []
      
      // User menu buttons
      if (user) {
        right.push(
          <Button
            key="profile-button"
            variant="ghost"
            size="sm"
            onClick={() => router.push('/profile')}
            leftIcon={<UserIcon className="w-5 h-5" />}
          >
            Profile
          </Button>
        )
      } else if (onSignUp) {
        right.push(
          <Button
            key="signup-button"
            variant="primary"
            size="sm"
            onClick={onSignUp}
          >
            Sign Up
          </Button>
        )
      }
      
      // OpenFrame CTA - Get Started button
      right.push(
        <Button
          key="get-started-button"
          variant="outline"
          size="sm"
          onClick={() => window.open('https://github.com/openframe-dev', '_blank')}
        >
          Get Started
        </Button>
      )
      
      return { left, right }
    },
    mobileNav: {
      menuIcon: <HamburgerIcon className="w-6 h-6 text-ods-text-primary" />,
      closeIcon: <XIcon className="w-4 h-4 text-ods-text-primary" />
    }
  },
  footer: {
    showWaitlist: false,
    logo: {
      getElement: () => <OpenFrameLogo width={32} height={32} className="flex-shrink-0 w-8 h-8" />
    },
    name: {
      getElement: () => (
        <span className="font-heading text-heading-5 font-semibold text-ods-text-primary">
          OpenFrame
        </span>
      )
    },
    sections: [
      {
        title: 'RESOURCES',
        links: [
          { href: '/docs', label: 'Documentation' },
          { href: '/examples', label: 'Examples' }
        ]
      },
      {
        title: 'COMPANY',
        links: [
          { href: '/about', label: 'About' },
          { href: '/contact', label: 'Contact' }
        ]
      }
    ]
  },
  contact: {
    email: 'hello@openframe.dev',
    supportUrl: '/support'
  },
  social: {
    github: 'https://github.com/openframe-dev',
    twitter: 'https://twitter.com/openframe_dev'
  }
}