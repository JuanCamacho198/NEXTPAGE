export const messagesEn = {
  'nav.aria': 'Main navigation',
  'nav.home': 'Home',
  'nav.catalog': 'Catalog',
  'nav.submit': 'Submit an addon',
  'nav.docs': 'Documentation',
  'nav.langToggleAria': 'Switch language',
  'footer.tagline': 'Reading, your way: books and addons without leaving the app.',
  'footer.aria': 'Site footer',
  'index.heroTitle': 'Read your books, extend your reader',
  'index.heroSubtitle':
    'NextPage brings your library, reading stats, and a growing addon catalog together in one place. The web catalog arrives soon.',
  'index.heroCta': 'Coming soon',
} as const;

export type UiKey = keyof typeof messagesEn;
