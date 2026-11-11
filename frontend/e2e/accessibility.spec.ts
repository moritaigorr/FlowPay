import { expect, test } from '@playwright/test'

test('keyboard access, responsive widths and 200 percent zoom', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 })
  await page.goto('/login')
  await page.getByRole('button', { name: /explore demo workspace/i }).click()
  await expect(page).toHaveURL(/\/payments$/)
  await page.keyboard.press('Tab')
  await expect(page.getByRole('link', { name: 'Skip to content' })).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('main')).toBeFocused()
  await page.getByRole('button', { name: 'Create payment' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: 'Close form' })).toBeVisible()
  for (const width of [375, 768, 1440]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/payments')
    await expect(page.getByRole('heading', { name: 'Payments' })).toBeVisible()
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), `horizontal overflow at ${width}px`).toBe(true)
  }
  // A 720 physical-pixel viewport at 200% browser zoom exposes about 360 CSS pixels.
  await page.setViewportSize({ width: 360, height: 900 })
  await expect(page.getByRole('heading', { name: 'Payments' })).toBeAttached()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
})

test('proxy rejects demo creation without CSRF and keeps session cookie HttpOnly', async ({ page }) => {
  await page.goto('/login')
  expect(await page.evaluate(async () => (await fetch('/api/v1/demo/sessions', { method: 'POST' })).status)).toBe(403)
  await page.getByRole('button', { name: /explore demo workspace/i }).click()
  await expect(page).toHaveURL(/\/payments$/)
  expect(await page.evaluate(() => document.cookie)).not.toMatch(/SESSION|JSESSIONID/i)
})
