// Adapted from the official shadcn/ui new-york Button registry (MIT).
// Source: https://ui.shadcn.com/r/styles/new-york/button.json
import * as React from 'react'
import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '../../lib/utils'

const buttonVariants = cva('button', {
  variants: {
    variant: {
      default: 'button-primary',
      secondary: 'button-secondary',
      ghost: 'button-quiet',
      destructive: 'button-destructive',
    },
  },
  defaultVariants: { variant: 'default' },
})

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>, VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

export const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, asChild = false, type, ...props }, ref) => {
    const Component = asChild ? Slot : 'button'
    return <Component ref={ref} type={asChild ? undefined : (type ?? 'button')} className={cn(buttonVariants({ variant, className }))} {...props} />
  },
)
Button.displayName = 'Button'
