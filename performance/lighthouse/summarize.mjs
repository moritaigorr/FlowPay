import { readFile, writeFile } from 'node:fs/promises'

const [reportPath, summaryPath, page, device, repetition] = process.argv.slice(2)
if (!reportPath || !summaryPath || !page || !device || !repetition) throw new Error('Usage: summarize.mjs <report> <summary> <page> <device> <repetition>.')

const report = JSON.parse(await readFile(reportPath, 'utf8'))
const audits = report.audits ?? {}
const metric = id => audits[id]?.numericValue ?? null
const networkItems = audits['network-requests']?.details?.items ?? []
const summary = {
  page,
  device,
  repetition: Number(repetition),
  finalUrlPath: new URL(report.finalDisplayedUrl).pathname.replace(/\/payments\/[\da-f-]{36}$/i, '/payments/{synthetic-id}'),
  performanceScore: report.categories?.performance?.score ?? null,
  firstContentfulPaintMs: metric('first-contentful-paint'),
  largestContentfulPaintMs: metric('largest-contentful-paint'),
  speedIndexMs: metric('speed-index'),
  totalBlockingTimeMs: metric('total-blocking-time'),
  cumulativeLayoutShift: metric('cumulative-layout-shift'),
  totalTransferBytes: metric('total-byte-weight'),
  scriptTransferBytes: networkItems.filter(item => item.resourceType === 'Script').reduce((sum, item) => sum + (item.transferSize ?? 0), 0),
  diagnostics: ['render-blocking-resources', 'unused-javascript', 'uses-optimized-images', 'uses-text-compression']
    .map(id => ({ id, score: audits[id]?.score ?? null, displayValue: audits[id]?.displayValue ?? null })),
}
if (summary.finalUrlPath !== new URL(report.requestedUrl).pathname) throw new Error(`Lighthouse navigated away from the requested ${page} route.`)
if (summary.performanceScore === null || summary.largestContentfulPaintMs === null) throw new Error(`Lighthouse metrics are missing for ${page}/${device}.`)
await writeFile(summaryPath, JSON.stringify(summary, null, 2) + '\n', { flag: 'wx' })
process.stdout.write(JSON.stringify(summary) + '\n')
