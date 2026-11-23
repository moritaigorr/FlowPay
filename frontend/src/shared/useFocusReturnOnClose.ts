import { useEffect, useRef } from 'react'

export function useFocusReturnOnClose(isOpen: boolean) {
  const headingRef = useRef<HTMLHeadingElement>(null)
  const requested = useRef(false)

  useEffect(() => {
    if (isOpen || !requested.current) return
    requested.current = false
    headingRef.current?.focus()
  }, [isOpen])

  return [headingRef, () => { requested.current = true }] as const
}
