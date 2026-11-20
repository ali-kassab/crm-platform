/**
 * Sector defaults (PRODUCT_AUDIT #38: "sector choice → defaults"). The sector only changes
 * suggestions and the order of the first steps; it never switches features on or off.
 */
export const SECTORS = ['FIT_OUT', 'SECURITY_SYSTEMS', 'MAINTENANCE'] as const
export type Sector = (typeof SECTORS)[number]

export type FirstStep = 'customer' | 'equipment' | 'plan' | 'job' | 'visit'

/** What a new workspace of each sector usually does first, in order. */
export const FIRST_STEPS: Record<Sector, readonly FirstStep[]> = {
  FIT_OUT: ['customer', 'job', 'visit'],
  SECURITY_SYSTEMS: ['customer', 'equipment', 'job', 'visit'],
  MAINTENANCE: ['customer', 'plan', 'job'],
}

export function isSector(value: string | null | undefined): value is Sector {
  return (SECTORS as readonly string[]).includes(value ?? '')
}

/** Suggested type for a new work order: installations for fit-out and security, repairs otherwise. */
export function defaultJobType(sector: string | null | undefined): 'INSTALLATION' | 'CORRECTIVE_REPAIR' {
  return sector === 'FIT_OUT' || sector === 'SECURITY_SYSTEMS' ? 'INSTALLATION' : 'CORRECTIVE_REPAIR'
}
