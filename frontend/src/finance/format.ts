export function formatMinor(value: string) {
  const amount = BigInt(value)
  const negative = amount < 0n
  const absolute = negative ? -amount : amount
  const whole = new Intl.NumberFormat('pt-BR').format(absolute / 100n)
  const cents = String(absolute % 100n).padStart(2, '0')
  return `${negative ? '−' : ''}R$ ${whole},${cents}`
}

export function parseBrlToMinor(value: string) {
  const input = value.trim()
  const match = input.match(/^(-?)(?:(\d+|\d{1,3}(?:\.\d{3})+))(?:,(\d{1,2}))?$/)
  if (!match) throw new Error('Informe um valor válido em reais, como 1.234,56.')
  const whole = BigInt(match[2].replaceAll('.', ''))
  const cents = BigInt((match[3] ?? '').padEnd(2, '0'))
  const amount = whole * 100n + cents
  return (match[1] ? -amount : amount).toString()
}

export function formatLocalDate(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeZone: 'UTC' })
    .format(new Date(`${value}T00:00:00Z`))
}
