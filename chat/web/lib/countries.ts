export interface Country {
  iso: string
  name: string
  dial: string
}

export const COUNTRIES: Country[] = [
  { iso: 'IN', name: 'India', dial: '91' },
  { iso: 'US', name: 'United States', dial: '1' },
  { iso: 'GB', name: 'United Kingdom', dial: '44' },
  { iso: 'AE', name: 'United Arab Emirates', dial: '971' },
  { iso: 'AF', name: 'Afghanistan', dial: '93' },
  { iso: 'AR', name: 'Argentina', dial: '54' },
  { iso: 'AU', name: 'Australia', dial: '61' },
  { iso: 'AT', name: 'Austria', dial: '43' },
  { iso: 'BD', name: 'Bangladesh', dial: '880' },
  { iso: 'BE', name: 'Belgium', dial: '32' },
  { iso: 'BT', name: 'Bhutan', dial: '975' },
  { iso: 'BR', name: 'Brazil', dial: '55' },
  { iso: 'CA', name: 'Canada', dial: '1' },
  { iso: 'CL', name: 'Chile', dial: '56' },
  { iso: 'CN', name: 'China', dial: '86' },
  { iso: 'CO', name: 'Colombia', dial: '57' },
  { iso: 'CZ', name: 'Czechia', dial: '420' },
  { iso: 'DK', name: 'Denmark', dial: '45' },
  { iso: 'EG', name: 'Egypt', dial: '20' },
  { iso: 'FI', name: 'Finland', dial: '358' },
  { iso: 'FR', name: 'France', dial: '33' },
  { iso: 'DE', name: 'Germany', dial: '49' },
  { iso: 'GH', name: 'Ghana', dial: '233' },
  { iso: 'GR', name: 'Greece', dial: '30' },
  { iso: 'HK', name: 'Hong Kong', dial: '852' },
  { iso: 'HU', name: 'Hungary', dial: '36' },
  { iso: 'ID', name: 'Indonesia', dial: '62' },
  { iso: 'IE', name: 'Ireland', dial: '353' },
  { iso: 'IL', name: 'Israel', dial: '972' },
  { iso: 'IT', name: 'Italy', dial: '39' },
  { iso: 'JP', name: 'Japan', dial: '81' },
  { iso: 'KE', name: 'Kenya', dial: '254' },
  { iso: 'KR', name: 'South Korea', dial: '82' },
  { iso: 'KW', name: 'Kuwait', dial: '965' },
  { iso: 'LK', name: 'Sri Lanka', dial: '94' },
  { iso: 'MY', name: 'Malaysia', dial: '60' },
  { iso: 'MV', name: 'Maldives', dial: '960' },
  { iso: 'MX', name: 'Mexico', dial: '52' },
  { iso: 'MA', name: 'Morocco', dial: '212' },
  { iso: 'NP', name: 'Nepal', dial: '977' },
  { iso: 'NL', name: 'Netherlands', dial: '31' },
  { iso: 'NZ', name: 'New Zealand', dial: '64' },
  { iso: 'NG', name: 'Nigeria', dial: '234' },
  { iso: 'NO', name: 'Norway', dial: '47' },
  { iso: 'OM', name: 'Oman', dial: '968' },
  { iso: 'PK', name: 'Pakistan', dial: '92' },
  { iso: 'PE', name: 'Peru', dial: '51' },
  { iso: 'PH', name: 'Philippines', dial: '63' },
  { iso: 'PL', name: 'Poland', dial: '48' },
  { iso: 'PT', name: 'Portugal', dial: '351' },
  { iso: 'QA', name: 'Qatar', dial: '974' },
  { iso: 'RO', name: 'Romania', dial: '40' },
  { iso: 'RU', name: 'Russia', dial: '7' },
  { iso: 'SA', name: 'Saudi Arabia', dial: '966' },
  { iso: 'SG', name: 'Singapore', dial: '65' },
  { iso: 'ZA', name: 'South Africa', dial: '27' },
  { iso: 'ES', name: 'Spain', dial: '34' },
  { iso: 'SE', name: 'Sweden', dial: '46' },
  { iso: 'CH', name: 'Switzerland', dial: '41' },
  { iso: 'TW', name: 'Taiwan', dial: '886' },
  { iso: 'TH', name: 'Thailand', dial: '66' },
  { iso: 'TR', name: 'Türkiye', dial: '90' },
  { iso: 'UA', name: 'Ukraine', dial: '380' },
  { iso: 'VN', name: 'Vietnam', dial: '84' },
]

export const DEFAULT_COUNTRY = COUNTRIES[0]

const BY_LANGUAGE: Record<string, string> = { hi: 'IN', bn: 'BD', pt: 'BR', vi: 'VN' }

const BY_TIME_ZONE: Record<string, string> = {
  'Asia/Kolkata': 'IN', 'Asia/Calcutta': 'IN', 'Asia/Dhaka': 'BD', 'Asia/Dacca': 'BD',
  'Asia/Ho_Chi_Minh': 'VN', 'Asia/Saigon': 'VN', 'America/Sao_Paulo': 'BR', 'America/Bahia': 'BR',
  'America/Fortaleza': 'BR', 'America/Recife': 'BR', 'America/Manaus': 'BR', 'America/Belem': 'BR',
  'America/Cuiaba': 'BR', 'America/Campo_Grande': 'BR', 'America/Porto_Velho': 'BR', 'America/Boa_Vista': 'BR',
  'America/Rio_Branco': 'BR', 'America/Maceio': 'BR', 'America/Araguaina': 'BR', 'America/Santarem': 'BR',
  'America/Noronha': 'BR', 'Asia/Kathmandu': 'NP', 'Asia/Colombo': 'LK', 'Asia/Karachi': 'PK', 'Asia/Dubai': 'AE',
}

/**
 * The likely phone country: the device's time zone first (an Indian phone set to "English (US)" is still in
 * Asia/Kolkata), then the browser's languages ("bn-BD" → Bangladesh, "pt" → Brazil), else India.
 */
export function guessCountry(tags: readonly string[], timeZone?: string): Country {
  const byZone = timeZone ? BY_TIME_ZONE[timeZone] : undefined
  if (byZone) return COUNTRIES.find((c) => c.iso === byZone) ?? DEFAULT_COUNTRY
  for (const tag of tags) {
    const [lang, region] = tag.split('-')
    const byRegion = region && COUNTRIES.find((c) => c.iso === region.toUpperCase())
    if (byRegion) return byRegion
    const iso = BY_LANGUAGE[lang?.toLowerCase()]
    if (iso) return COUNTRIES.find((c) => c.iso === iso) ?? DEFAULT_COUNTRY
  }
  return DEFAULT_COUNTRY
}

export const SHOWCASE_FLAGS = ['IN', 'BR', 'JP', 'NG', 'FR', 'TR', 'AR', 'US', 'PT', 'AE', 'KR', 'MX', 'GB', 'ID']
