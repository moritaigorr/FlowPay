import { readFile, writeFile } from 'node:fs/promises'

const [inputPath, outputPath] = process.argv.slice(2)
if (!inputPath || !outputPath) throw new Error('Usage: aggregate.mjs <individual-results.json> <median-results.json>.')

const runs = JSON.parse(await readFile(inputPath, 'utf8'))
const metrics = ['performanceScore', 'firstContentfulPaintMs', 'largestContentfulPaintMs', 'speedIndexMs', 'totalBlockingTimeMs', 'cumulativeLayoutShift', 'totalTransferBytes', 'scriptTransferBytes']
const medians = []
for (const page of ['overview', 'payments', 'payment-detail']) {
  for (const device of ['mobile', 'desktop']) {
    const samples = runs.filter(run => run.page === page && run.device === device).sort((a, b) => a.repetition - b.repetition)
    if (samples.length !== 3 || samples.map(sample => sample.repetition).join(',') !== '1,2,3'
      || samples.some(run => run.finalUrlPath !== (page === 'payment-detail' ? '/payments/{synthetic-id}' : `/${page}`))) {
      throw new Error(`Expected unique repetitions 1,2,3 on the correct route for ${device}/${page}.`)
    }
    const result = { page, device, sampleCount: samples.length, repetitions: samples.map(sample => sample.repetition) }
    for (const metric of metrics) {
      const values = samples.map(sample => sample[metric]).sort((a, b) => a - b)
      result[metric] = values[Math.floor(values.length / 2)]
    }
    result.diagnostics = samples[0].diagnostics
    medians.push(result)
  }
}
await writeFile(outputPath, JSON.stringify(medians, null, 2) + '\n', { flag: 'wx' })
process.stdout.write(JSON.stringify(medians, null, 2) + '\n')
