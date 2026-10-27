import test from 'node:test'
import assert from 'node:assert/strict'
import { COUNTRIES } from '../countries.ts'
import { currencyOf, localPrice } from '../currency.ts'

test('every sign-up country has a money with a rate', () => {
  for (const c of COUNTRIES) assert.ok(Intl.NumberFormat('en', { style: 'currency', currency: currencyOf(c.iso) }).format(1), c.iso)
})

test('rupees in India, rough local money elsewhere', () => {
  assert.equal(localPrice(79, 'IN', 'en-IN'), '₹79')
  assert.equal(localPrice(79, 'KR', 'ko-KR'), '≈ ₩1,300')
  assert.equal(localPrice(79, 'US', 'en-US'), '≈ $0.90')
  assert.equal(localPrice(79, 'JP', 'ja-JP'), '≈ ￥130')
  assert.equal(localPrice(79, 'VE', 'en-US'), '≈ $0.90') // kept in dollars
  assert.equal(localPrice(79, null, 'en-US'), '≈ $0.90')
  assert.equal(localPrice(79, 'ID', 'en-US').replace(/\s/g, ' '), '≈ IDR 15,000')
})
