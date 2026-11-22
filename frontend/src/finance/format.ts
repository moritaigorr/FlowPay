export function formatMinor(value: string) {
  const amount = BigInt(value)
  const negative = amount < 0n
  const absolute = negative ? -amount : amount
  const whole = new Intl.NumberFormat('pt-BR').format(absolute / 100n)
  const cents = String(absolute % 100n).padStart(2, '0')
  return `${negative ? '−' : ''}R$ ${whole},${cents}`
}

export function formatLocalDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeZone: 'UTC' })
    .format(new Date(`${value}T00:00:00Z`))
}
