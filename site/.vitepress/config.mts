import { defineConfig } from 'vitepress'

export default defineConfig({
  title: 'retask4j',
  description: 'A Redis-based distributed async task queue with a unique HTTP proxy mode',
  lang: 'en-US',
  base: '/retask4j/',
  lastUpdated: true,
  cleanUrls: true,
  srcDir: '.',
  ignoreDeadLinks: true,

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/retask4j/favicon.svg' }],
    ['meta', { name: 'theme-color', content: '#2563eb' }],
    ['meta', { property: 'og:title', content: 'retask4j' }],
    ['meta', { property: 'og:description', content: 'A Redis-based distributed async task queue with a unique HTTP proxy mode' }],
  ],

  themeConfig: {
    nav: [
      { text: 'Guide', link: '/guide/overview' },
      { text: 'Concepts', link: '/guide/concepts' },
      { text: 'HTTP Proxy', link: '/guide/http-proxy' },
      { text: 'REST API', link: '/guide/redis-protocol' },
      { text: 'Config', link: '/guide/configuration' },
      { text: 'v1.0.0', link: 'https://github.com/funcommons/retask4j/releases/tag/v1.0.0' },
    ],

    sidebar: {
      '/guide/': [
        {
          text: 'Introduction',
          items: [
            { text: 'Overview', link: '/guide/overview' },
            { text: 'Quickstart', link: '/guide/quickstart' },
            { text: 'Concepts', link: '/guide/concepts' },
            { text: 'Best Practices', link: '/guide/best-practices' },
          ],
        },
        {
          text: 'Features',
          items: [
            { text: 'HTTP Proxy Mode', link: '/guide/http-proxy' },
            { text: 'REST API & Redis Protocol', link: '/guide/redis-protocol' },
            { text: 'Configuration', link: '/guide/configuration' },
            { text: 'API Reference', link: '/guide/api-reference' },
            { text: 'Comparison', link: '/guide/diff-table' },
          ],
        },
        {
          text: 'About',
          items: [
            { text: 'CHANGELOG', link: '/changelog' },
            { text: 'Contributing', link: '/contributing' },
            { text: 'License', link: '/license' },
          ],
        },
      ],
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/funcommons/retask4j' },
    ],

    footer: {
      message: 'Released under the Apache License 2.0.',
      copyright: `Copyright © 2018-${new Date().getFullYear()} fun.commons`,
    },

    editLink: {
      pattern: 'https://github.com/funcommons/retask4j/edit/main/site/:path',
      text: 'Edit this page on GitHub',
    },

    outline: {
      level: [2, 3],
      label: 'On this page',
    },

    search: {
      provider: 'local',
    },
  },
})
