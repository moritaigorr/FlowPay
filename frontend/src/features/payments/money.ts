const maximumAmount = 99_999_999_999_999_999n
const decimalPattern = /^(?:0|[1-9]\d{0,16})(?:\.\d{1,2})?$/

/** Validate an unformatted user-entered decimal and return its exact two-place wire representation. */
export function parseAmount(value: string): string {
  if (!decimalPattern.test(value)) throw new Error('Enter an amount with up to two decimal places.')
  const [whole, fraction = ''] = value.split('.')
  const amountMinor = BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'))
  if (amountMinor === 0n) throw new Error('Amount must be greater than zero.')
  if (BigInt(whole) > maximumAmount) throw new Error('Amount exceeds the supported maximum.')
  return `${whole}.${fraction.padEnd(2, '0')}`
}

/** Format a canonical decimal string without converting the amount to a Number. */
export function formatBrl(amount: string): string {
  const canonical = parseAmount(amount)
  const [whole, fraction] = canonical.split('.')
  const groupedWhole = new Intl.NumberFormat('pt-BR', { maximumFractionDigits: 0 }).format(BigInt(whole))
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })
    .formatToParts(0)
    .map((part) => part.type === 'integer' ? groupedWhole : part.type === 'fraction' ? fraction : part.value)
    .join('')
}
