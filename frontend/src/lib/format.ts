import i18n from '@/i18n'

const locale = () => (i18n.language === 'ar' ? 'ar-EG' : 'en-EG')

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return ''
  return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso))
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return ''
  return new Intl.DateTimeFormat(locale(), { dateStyle: 'medium' }).format(new Date(iso))
}

export function formatNumber(value: number): string {
  return new Intl.NumberFormat(locale()).format(value)
}

/** Money arrives as a decimal string ("1500.00"); shown in the workspace currency. */
export function formatMoney(amount: string | null | undefined, currency = 'EGP'): string {
  if (amount == null) return ''
  return new Intl.NumberFormat(locale(), { style: 'currency', currency, maximumFractionDigits: 2 }).format(Number(amount))
}

/** `<input type="datetime-local">` value ↔ ISO instant in the browser's time zone. */
export function toLocalInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

export function fromLocalInput(value: string): string {
  return new Date(value).toISOString()
}
