export interface Country {
  iso: string
  name: string
  dial: string
}

/** Every country the sign-up offers, A to Z (the likely one is chosen for the person, see guessCountry). */
export const COUNTRIES: Country[] = [
  { iso: 'AF', name: 'Afghanistan', dial: '93' },
  { iso: 'DZ', name: 'Algeria', dial: '213' },
  { iso: 'AR', name: 'Argentina', dial: '54' },
  { iso: 'AM', name: 'Armenia', dial: '374' },
  { iso: 'AU', name: 'Australia', dial: '61' },
  { iso: 'AT', name: 'Austria', dial: '43' },
  { iso: 'AZ', name: 'Azerbaijan', dial: '994' },
  { iso: 'BH', name: 'Bahrain', dial: '973' },
  { iso: 'BD', name: 'Bangladesh', dial: '880' },
  { iso: 'BY', name: 'Belarus', dial: '375' },
  { iso: 'BE', name: 'Belgium', dial: '32' },
  { iso: 'BT', name: 'Bhutan', dial: '975' },
  { iso: 'BO', name: 'Bolivia', dial: '591' },
  { iso: 'BR', name: 'Brazil', dial: '55' },
  { iso: 'BG', name: 'Bulgaria', dial: '359' },
  { iso: 'KH', name: 'Cambodia', dial: '855' },
  { iso: 'CM', name: 'Cameroon', dial: '237' },
  { iso: 'CA', name: 'Canada', dial: '1' },
  { iso: 'CL', name: 'Chile', dial: '56' },
  { iso: 'CN', name: 'China', dial: '86' },
  { iso: 'CO', name: 'Colombia', dial: '57' },
  { iso: 'CR', name: 'Costa Rica', dial: '506' },
  { iso: 'HR', name: 'Croatia', dial: '385' },
  { iso: 'CZ', name: 'Czechia', dial: '420' },
  { iso: 'CI', name: 'Côte d’Ivoire', dial: '225' },
  { iso: 'DK', name: 'Denmark', dial: '45' },
  { iso: 'DO', name: 'Dominican Republic', dial: '1' },
  { iso: 'EC', name: 'Ecuador', dial: '593' },
  { iso: 'EG', name: 'Egypt', dial: '20' },
  { iso: 'ET', name: 'Ethiopia', dial: '251' },
  { iso: 'FI', name: 'Finland', dial: '358' },
  { iso: 'FR', name: 'France', dial: '33' },
  { iso: 'GE', name: 'Georgia', dial: '995' },
  { iso: 'DE', name: 'Germany', dial: '49' },
  { iso: 'GH', name: 'Ghana', dial: '233' },
  { iso: 'GR', name: 'Greece', dial: '30' },
  { iso: 'GT', name: 'Guatemala', dial: '502' },
  { iso: 'HK', name: 'Hong Kong', dial: '852' },
  { iso: 'HU', name: 'Hungary', dial: '36' },
  { iso: 'IN', name: 'India', dial: '91' },
  { iso: 'ID', name: 'Indonesia', dial: '62' },
  { iso: 'IR', name: 'Iran', dial: '98' },
  { iso: 'IQ', name: 'Iraq', dial: '964' },
  { iso: 'IE', name: 'Ireland', dial: '353' },
  { iso: 'IL', name: 'Israel', dial: '972' },
  { iso: 'IT', name: 'Italy', dial: '39' },
  { iso: 'JP', name: 'Japan', dial: '81' },
  { iso: 'JO', name: 'Jordan', dial: '962' },
  { iso: 'KZ', name: 'Kazakhstan', dial: '7' },
  { iso: 'KE', name: 'Kenya', dial: '254' },
  { iso: 'KW', name: 'Kuwait', dial: '965' },
  { iso: 'LA', name: 'Laos', dial: '856' },
  { iso: 'LB', name: 'Lebanon', dial: '961' },
  { iso: 'MY', name: 'Malaysia', dial: '60' },
  { iso: 'MV', name: 'Maldives', dial: '960' },
  { iso: 'MX', name: 'Mexico', dial: '52' },
  { iso: 'MA', name: 'Morocco', dial: '212' },
  { iso: 'MM', name: 'Myanmar', dial: '95' },
  { iso: 'NP', name: 'Nepal', dial: '977' },
  { iso: 'NL', name: 'Netherlands', dial: '31' },
  { iso: 'NZ', name: 'New Zealand', dial: '64' },
  { iso: 'NG', name: 'Nigeria', dial: '234' },
  { iso: 'NO', name: 'Norway', dial: '47' },
  { iso: 'OM', name: 'Oman', dial: '968' },
  { iso: 'PK', name: 'Pakistan', dial: '92' },
  { iso: 'PA', name: 'Panama', dial: '507' },
  { iso: 'PY', name: 'Paraguay', dial: '595' },
  { iso: 'PE', name: 'Peru', dial: '51' },
  { iso: 'PH', name: 'Philippines', dial: '63' },
  { iso: 'PL', name: 'Poland', dial: '48' },
  { iso: 'PT', name: 'Portugal', dial: '351' },
  { iso: 'QA', name: 'Qatar', dial: '974' },
  { iso: 'RO', name: 'Romania', dial: '40' },
  { iso: 'RU', name: 'Russia', dial: '7' },
  { iso: 'SA', name: 'Saudi Arabia', dial: '966' },
  { iso: 'SN', name: 'Senegal', dial: '221' },
  { iso: 'RS', name: 'Serbia', dial: '381' },
  { iso: 'SG', name: 'Singapore', dial: '65' },
  { iso: 'SK', name: 'Slovakia', dial: '421' },
  { iso: 'ZA', name: 'South Africa', dial: '27' },
  { iso: 'KR', name: 'South Korea', dial: '82' },
  { iso: 'ES', name: 'Spain', dial: '34' },
  { iso: 'LK', name: 'Sri Lanka', dial: '94' },
  { iso: 'SE', name: 'Sweden', dial: '46' },
  { iso: 'CH', name: 'Switzerland', dial: '41' },
  { iso: 'TW', name: 'Taiwan', dial: '886' },
  { iso: 'TZ', name: 'Tanzania', dial: '255' },
  { iso: 'TH', name: 'Thailand', dial: '66' },
  { iso: 'TN', name: 'Tunisia', dial: '216' },
  { iso: 'TR', name: 'Türkiye', dial: '90' },
  { iso: 'UG', name: 'Uganda', dial: '256' },
  { iso: 'UA', name: 'Ukraine', dial: '380' },
  { iso: 'AE', name: 'United Arab Emirates', dial: '971' },
  { iso: 'GB', name: 'United Kingdom', dial: '44' },
  { iso: 'US', name: 'United States', dial: '1' },
  { iso: 'UY', name: 'Uruguay', dial: '598' },
  { iso: 'UZ', name: 'Uzbekistan', dial: '998' },
  { iso: 'VE', name: 'Venezuela', dial: '58' },
  { iso: 'VN', name: 'Vietnam', dial: '84' },
  { iso: 'ZW', name: 'Zimbabwe', dial: '263' },
]

const byIso = (iso: string) => COUNTRIES.find((c) => c.iso === iso)

/** When nothing on the device says where it is (rare: browsers report a region or a time zone). */
export const DEFAULT_COUNTRY = byIso('US')!

/** A language spoken mostly in one country (a tag without region: "ja", "vi"). Languages of many countries are left out. */
const BY_LANGUAGE: Record<string, string> = {
  hi: 'IN', mr: 'IN', gu: 'IN', ta: 'IN', te: 'IN', kn: 'IN', ml: 'IN', pa: 'IN', bn: 'BD', ur: 'PK', ne: 'NP',
  si: 'LK', pt: 'BR', vi: 'VN', th: 'TH', id: 'ID', ms: 'MY', fil: 'PH', tl: 'PH', ja: 'JP', ko: 'KR', tr: 'TR',
  pl: 'PL', ro: 'RO', uk: 'UA', it: 'IT', nl: 'NL', fa: 'IR', he: 'IL', iw: 'IL', sw: 'KE', el: 'GR', cs: 'CZ',
  hu: 'HU', sv: 'SE', da: 'DK', fi: 'FI', nb: 'NO', no: 'NO', bg: 'BG', sr: 'RS', hr: 'HR', sk: 'SK', km: 'KH',
  my: 'MM', lo: 'LA', am: 'ET', kk: 'KZ', uz: 'UZ', az: 'AZ', ka: 'GE', hy: 'AM',
}

const BY_TIME_ZONE: Record<string, string> = {
  'Asia/Kolkata': 'IN', 'Asia/Calcutta': 'IN', 'Asia/Dhaka': 'BD', 'Asia/Dacca': 'BD',
  'Asia/Ho_Chi_Minh': 'VN', 'Asia/Saigon': 'VN', 'America/Sao_Paulo': 'BR', 'America/Bahia': 'BR',
  'America/Fortaleza': 'BR', 'America/Recife': 'BR', 'America/Manaus': 'BR', 'America/Belem': 'BR',
  'America/Cuiaba': 'BR', 'America/Campo_Grande': 'BR', 'America/Porto_Velho': 'BR', 'America/Boa_Vista': 'BR',
  'America/Rio_Branco': 'BR', 'America/Maceio': 'BR', 'America/Araguaina': 'BR', 'America/Santarem': 'BR',
  'America/Noronha': 'BR', 'Asia/Kathmandu': 'NP', 'Asia/Colombo': 'LK', 'Asia/Karachi': 'PK', 'Asia/Dubai': 'AE',
  'Asia/Bangkok': 'TH', 'Asia/Jakarta': 'ID', 'Asia/Makassar': 'ID', 'Asia/Jayapura': 'ID', 'Asia/Kuala_Lumpur': 'MY',
  'Asia/Manila': 'PH', 'Asia/Singapore': 'SG', 'Asia/Tokyo': 'JP', 'Asia/Seoul': 'KR', 'Asia/Shanghai': 'CN',
  'Asia/Taipei': 'TW', 'Asia/Hong_Kong': 'HK', 'Asia/Phnom_Penh': 'KH', 'Asia/Yangon': 'MM', 'Asia/Rangoon': 'MM',
  'Asia/Vientiane': 'LA', 'Asia/Tehran': 'IR', 'Asia/Baghdad': 'IQ', 'Asia/Riyadh': 'SA', 'Asia/Qatar': 'QA',
  'Asia/Kuwait': 'KW', 'Asia/Bahrain': 'BH', 'Asia/Muscat': 'OM', 'Asia/Amman': 'JO', 'Asia/Beirut': 'LB',
  'Asia/Jerusalem': 'IL', 'Asia/Tel_Aviv': 'IL', 'Asia/Almaty': 'KZ', 'Asia/Tashkent': 'UZ', 'Asia/Baku': 'AZ',
  'Asia/Tbilisi': 'GE', 'Asia/Yerevan': 'AM', 'Europe/Istanbul': 'TR', 'Europe/Moscow': 'RU', 'Europe/Kyiv': 'UA',
  'Europe/Kiev': 'UA', 'Europe/Minsk': 'BY', 'Europe/Warsaw': 'PL', 'Europe/Bucharest': 'RO', 'Europe/Sofia': 'BG',
  'Europe/Belgrade': 'RS', 'Europe/Zagreb': 'HR', 'Europe/Budapest': 'HU', 'Europe/Prague': 'CZ',
  'Europe/Bratislava': 'SK', 'Europe/Athens': 'GR', 'Europe/Berlin': 'DE', 'Europe/Vienna': 'AT',
  'Europe/Zurich': 'CH', 'Europe/Paris': 'FR', 'Europe/Brussels': 'BE', 'Europe/Amsterdam': 'NL',
  'Europe/Madrid': 'ES', 'Europe/Lisbon': 'PT', 'Europe/Rome': 'IT', 'Europe/London': 'GB', 'Europe/Dublin': 'IE',
  'Europe/Stockholm': 'SE', 'Europe/Oslo': 'NO', 'Europe/Copenhagen': 'DK', 'Europe/Helsinki': 'FI',
  'Africa/Cairo': 'EG', 'Africa/Lagos': 'NG', 'Africa/Nairobi': 'KE', 'Africa/Accra': 'GH', 'Africa/Johannesburg': 'ZA',
  'Africa/Casablanca': 'MA', 'Africa/Algiers': 'DZ', 'Africa/Tunis': 'TN', 'Africa/Dar_es_Salaam': 'TZ',
  'Africa/Kampala': 'UG', 'Africa/Addis_Ababa': 'ET', 'Africa/Harare': 'ZW', 'Africa/Douala': 'CM', 'Africa/Dakar': 'SN',
  'Africa/Abidjan': 'CI', 'America/Mexico_City': 'MX', 'America/Bogota': 'CO', 'America/Lima': 'PE',
  'America/Santiago': 'CL', 'America/Argentina/Buenos_Aires': 'AR', 'America/Buenos_Aires': 'AR', 'America/Caracas': 'VE',
  'America/Guayaquil': 'EC', 'America/La_Paz': 'BO', 'America/Montevideo': 'UY', 'America/Asuncion': 'PY',
  'America/Guatemala': 'GT', 'America/Santo_Domingo': 'DO', 'America/Costa_Rica': 'CR', 'America/Panama': 'PA',
  'America/Toronto': 'CA', 'America/Vancouver': 'CA', 'America/New_York': 'US', 'America/Chicago': 'US',
  'America/Denver': 'US', 'America/Los_Angeles': 'US', 'America/Phoenix': 'US', 'America/Anchorage': 'US',
  'Pacific/Honolulu': 'US', 'Australia/Sydney': 'AU', 'Australia/Melbourne': 'AU', 'Australia/Brisbane': 'AU',
  'Australia/Perth': 'AU', 'Australia/Adelaide': 'AU', 'Pacific/Auckland': 'NZ', 'Indian/Maldives': 'MV',
  'Asia/Thimphu': 'BT',
}

/**
 * The likely phone country: the device's time zone first (a phone in Lagos set to "English (US)" is still in
 * Africa/Lagos), then the browser's languages ("bn-BD" → Bangladesh, "pt" → Brazil, "ja" → Japan), else the US.
 * The person can always pick another one.
 */
export function guessCountry(tags: readonly string[], timeZone?: string): Country {
  const byZone = timeZone ? BY_TIME_ZONE[timeZone] : undefined
  if (byZone) return byIso(byZone) ?? DEFAULT_COUNTRY
  for (const tag of tags) {
    const parts = tag.split(/[-_]/)
    const region = parts.slice(1).find((p) => /^[a-z]{2}$/i.test(p))
    const byRegion = region && byIso(region.toUpperCase())
    if (byRegion) return byRegion
    const iso = BY_LANGUAGE[parts[0]?.toLowerCase()]
    if (iso) return byIso(iso) ?? DEFAULT_COUNTRY
  }
  return DEFAULT_COUNTRY
}

export const SHOWCASE_FLAGS = ['IN', 'BR', 'JP', 'NG', 'FR', 'TR', 'AR', 'US', 'PT', 'AE', 'KR', 'MX', 'GB', 'ID']
