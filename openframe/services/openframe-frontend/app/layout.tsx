import type { Metadata } from 'next'
import './globals.css'
import '@flamingo/ui-kit/styles'
import { azeretMono, dmSans } from '@flamingo/ui-kit/fonts'

export const metadata: Metadata = {
  title: 'OpenFrame',
  description: 'Open-source application framework for device management',
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="en" suppressHydrationWarning className={`dark ${azeretMono.variable} ${dmSans.variable}`}>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
      </head>
      <body 
        suppressHydrationWarning 
        className="min-h-screen antialiased font-body"
        data-app-type="openframe"
      >
        <div className="relative flex min-h-screen flex-col">
          {children}
        </div>
      </body>
    </html>
  )
}