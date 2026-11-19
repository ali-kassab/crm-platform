import type { FieldValues, Path, UseFormSetError } from 'react-hook-form'
import { ApiError } from '@/lib/api'

/**
 * Maps backend validation errors (already localized by Accept-Language) onto form fields; any
 * other error becomes a form-level message. Returns the form-level message, if any.
 */
export function applyServerErrors<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly string[],
  fallback: string,
): string | null {
  if (!(error instanceof ApiError)) return fallback
  let mapped = false
  for (const e of error.fieldErrors) {
    const field = e.field.split('.')[0]
    if (fields.includes(field)) {
      setError(field as Path<T>, { type: 'server', message: e.message })
      mapped = true
    }
  }
  return mapped ? null : error.message
}
