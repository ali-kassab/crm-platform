// Bundle budget (R9): every JS chunk ≤ 200 kB gzip and the initial JS total ≤ 350 kB gzip.
import { readdirSync, readFileSync } from 'node:fs'
import { join } from 'node:path'
import { gzipSync } from 'node:zlib'

const dir = join(import.meta.dirname, '..', 'dist', 'assets')
const CHUNK_LIMIT = 200 * 1024
const TOTAL_LIMIT = 350 * 1024
let total = 0
let failed = false
for (const file of readdirSync(dir).filter((f) => f.endsWith('.js'))) {
  const size = gzipSync(readFileSync(join(dir, file))).length
  total += size
  const ok = size <= CHUNK_LIMIT
  failed ||= !ok
  console.log(`${ok ? 'ok  ' : 'FAIL'} ${(size / 1024).toFixed(1).padStart(7)} kB gzip  ${file}`)
}
console.log(`total ${(total / 1024).toFixed(1)} kB gzip (limit ${TOTAL_LIMIT / 1024} kB)`)
if (failed || total > TOTAL_LIMIT) {
  console.error('Bundle budget exceeded')
  process.exit(1)
}
