// 53: prices in the viewer's own money (the owner, 2026-10-27). The shop's prices are rupees (cosmetic_items.price_inr);
// elsewhere they show converted at a rough rate, marked "≈", to two significant digits (₩1,300, $0.90, €0.77).
// What a buyer pays is set per country in Google Play (its price templates), not here. Rates: units per US dollar,
// rounded, October 2026. Money that moves too fast to keep (ARS, VES, ZWL, IRR, LBP, AFN, BYN) shows dollars.

const PER_USD: Record<string, number> = {
  INR: 88, USD: 1, EUR: 0.86, GBP: 0.75, CHF: 0.8, SEK: 9.5, NOK: 10.1, DKK: 6.4, PLN: 3.65, CZK: 21, HUF: 335, RON: 4.35,
  RSD: 100, UAH: 41.5, RUB: 81, TRY: 41.5, KZT: 540, UZS: 12300, AMD: 385, AZN: 1.7, GEL: 2.7,
  KRW: 1400, JPY: 150, CNY: 7.1, HKD: 7.8, TWD: 30, SGD: 1.29, MYR: 4.2, THB: 32.5, IDR: 16500, PHP: 58, VND: 26300,
  BDT: 122, PKR: 282, NPR: 141, LKR: 302, MVR: 15.4, BTN: 88, MMK: 2100, KHR: 4000, LAK: 21600,
  AED: 3.67, SAR: 3.75, QAR: 3.64, KWD: 0.306, BHD: 0.376, OMR: 0.385, JOD: 0.709, ILS: 3.35, IQD: 1310,
  EGP: 48.5, MAD: 9.1, TND: 2.95, DZD: 130, NGN: 1530, GHS: 12, KES: 129, UGX: 3500, TZS: 2450, ETB: 140, ZAR: 17.6,
  XOF: 565, XAF: 565, BRL: 5.4, MXN: 18.5, CLP: 960, COP: 3950, PEN: 3.5, PYG: 7100, UYU: 40, BOB: 6.9, CRC: 505,
  GTQ: 7.7, DOP: 63, CAD: 1.39, AUD: 1.53, NZD: 1.72,
}

const EURO = 'AT BE BG DE ES FI FR GR HR IE IT NL PT SK'
const BY_COUNTRY: Record<string, string> = {
  ...Object.fromEntries(EURO.split(' ').map((c) => [c, 'EUR'])),
  IN: 'INR', US: 'USD', EC: 'USD', PA: 'USD', GB: 'GBP', CH: 'CHF', SE: 'SEK', NO: 'NOK', DK: 'DKK', PL: 'PLN', CZ: 'CZK',
  HU: 'HUF', RO: 'RON', RS: 'RSD', UA: 'UAH', RU: 'RUB', TR: 'TRY', KZ: 'KZT', UZ: 'UZS', AM: 'AMD', AZ: 'AZN', GE: 'GEL',
  KR: 'KRW', JP: 'JPY', CN: 'CNY', HK: 'HKD', TW: 'TWD', SG: 'SGD', MY: 'MYR', TH: 'THB', ID: 'IDR', PH: 'PHP', VN: 'VND',
  BD: 'BDT', PK: 'PKR', NP: 'NPR', LK: 'LKR', MV: 'MVR', BT: 'BTN', MM: 'MMK', KH: 'KHR', LA: 'LAK',
  AE: 'AED', SA: 'SAR', QA: 'QAR', KW: 'KWD', BH: 'BHD', OM: 'OMR', JO: 'JOD', IL: 'ILS', IQ: 'IQD',
  EG: 'EGP', MA: 'MAD', TN: 'TND', DZ: 'DZD', NG: 'NGN', GH: 'GHS', KE: 'KES', UG: 'UGX', TZ: 'TZS', ET: 'ETB', ZA: 'ZAR',
  CI: 'XOF', SN: 'XOF', CM: 'XAF', BR: 'BRL', MX: 'MXN', CL: 'CLP', CO: 'COP', PE: 'PEN', PY: 'PYG', UY: 'UYU', BO: 'BOB',
  CR: 'CRC', GT: 'GTQ', DO: 'DOP', CA: 'CAD', AU: 'AUD', NZ: 'NZD',
}

/** The money of a country (ISO-2); dollars when it isn't kept. */
export const currencyOf = (country: string | null | undefined) => BY_COUNTRY[country ?? ''] ?? 'USD'

/** A rupee price as the viewer reads it: exact in India, "≈" and converted everywhere else. */
export function localPrice(inr: number, country: string | null | undefined, locale: string): string {
  const currency = currencyOf(country)
  if (currency === 'INR') return new Intl.NumberFormat(locale, { style: 'currency', currency, maximumFractionDigits: 0 }).format(inr)
  // Two significant digits; cents shown in full ($0.90), none on big numbers (Rp14,000, not Rp14,000.00).
  const amount = Number(((inr / PER_USD.INR) * PER_USD[currency]).toPrecision(2))
  return `≈ ${new Intl.NumberFormat(locale, { style: 'currency', currency, ...(amount >= 100 && { maximumFractionDigits: 0 }) }).format(amount)}`
}
