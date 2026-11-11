/** Format a non-negative backend aggregate without narrowing its decimal string to Number. */
export function formatAggregateBrl(value: string) {
  const match = /^(\d+)\.(\d{2})$/.exec(value)
  if (!match) throw new Error('Aggregate amount must have exactly two decimal places.')
  const whole = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 }).format(BigInt(match[1]))
  const fraction = match[2]
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
    .formatToParts(0)
    .map((part) => part.type === 'integer' ? whole : part.type === 'fraction' ? fraction : part.value)
    .join('')
}
