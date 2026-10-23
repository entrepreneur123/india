import test from 'node:test'
import assert from 'node:assert/strict'
import { cleanContact, contactLink } from '../contacts.ts'

// The same rules as contacts_clean in server/migrations/044_friend_contacts.sql.

test('numbers: spaces, dots, dashes and brackets go; an optional + and 6-16 digits stay', () => {
  assert.equal(cleanContact('whatsapp', ' +91 98765-43210 '), '+919876543210')
  assert.equal(cleanContact('phone', '(022) 2345.6789'), '02223456789')
  for (const x of ['12345', '+1234567890123456789', '98765abc', '++919876543210', '91+98765']) assert.equal(cleanContact('phone', x), null, x)
})

test('handles: leading @ stripped, letters, digits and . _ - up to 40', () => {
  assert.equal(cleanContact('instagram', '@@priya.k_99'), 'priya.k_99')
  assert.equal(cleanContact('discord', 'a'.repeat(40)), 'a'.repeat(40))
  for (const x of ['a'.repeat(41), 'pri ya', 'प्रिया', 'a@b', '@']) assert.equal(cleanContact('telegram', x), null, x)
  assert.equal(cleanContact('line', '   '), '')
})

test('links open the app; KakaoTalk, WeChat and Discord have none (copied instead)', () => {
  assert.equal(contactLink('whatsapp', '+919876543210'), 'https://wa.me/919876543210')
  assert.equal(contactLink('phone', '+919876543210'), 'tel:+919876543210')
  assert.equal(contactLink('telegram', 'priya'), 'https://t.me/priya')
  assert.equal(contactLink('instagram', 'priya.k'), 'https://instagram.com/priya.k')
  assert.equal(contactLink('line', 'priya'), 'https://line.me/ti/p/~priya')
  assert.equal(contactLink('snapchat', 'priya'), 'https://snapchat.com/add/priya')
  for (const app of ['kakaotalk', 'wechat', 'discord'] as const) assert.equal(contactLink(app, 'priya'), null)
})
