import { defineConfig } from 'vitepress'

const enNav = [
  { text: 'Guide', link: '/en/guide/overview' },
  { text: 'Concepts', link: '/en/guide/concepts' },
  { text: 'HTTP Proxy', link: '/en/guide/http-proxy' },
  { text: 'REST API', link: '/en/guide/redis-protocol' },
  { text: 'Config', link: '/en/guide/configuration' },
  { text: 'v1.0.0', link: 'https://github.com/funcommons/retask4j/releases/tag/v1.0.0' },
]

const zhNav = [
  { text: '指南', link: '/zh-CN/guide/overview' },
  { text: '核心概念', link: '/zh-CN/guide/concepts' },
  { text: 'HTTP 代理', link: '/zh-CN/guide/http-proxy' },
  { text: 'REST API', link: '/zh-CN/guide/redis-protocol' },
  { text: '配置', link: '/zh-CN/guide/configuration' },
  { text: 'v1.0.0', link: 'https://github.com/funcommons/retask4j/releases/tag/v1.0.0' },
]

const enSidebar = {
  '/en/guide/': [
    {
      text: 'Introduction',
      items: [
        { text: 'Overview', link: '/en/guide/overview' },
        { text: 'Quickstart', link: '/en/guide/quickstart' },
        { text: 'Concepts', link: '/en/guide/concepts' },
        { text: 'Best Practices', link: '/en/guide/best-practices' },
      ],
    },
    {
      text: 'Features',
      items: [
        { text: 'HTTP Proxy Mode', link: '/en/guide/http-proxy' },
        { text: 'REST API & Redis Protocol', link: '/en/guide/redis-protocol' },
        { text: 'Configuration', link: '/en/guide/configuration' },
        { text: 'API Reference', link: '/en/guide/api-reference' },
        { text: 'Comparison', link: '/en/guide/diff-table' },
      ],
    },
    {
      text: 'About',
      items: [
        { text: 'CHANGELOG', link: '/en/changelog' },
        { text: 'Contributing', link: '/en/contributing' },
        { text: 'License', link: '/en/license' },
      ],
    },
  ],
}

const zhSidebar = {
  '/zh-CN/guide/': [
    {
      text: '介绍',
      items: [
        { text: '产品概述', link: '/zh-CN/guide/overview' },
        { text: '快速开始', link: '/zh-CN/guide/quickstart' },
        { text: '核心概念', link: '/zh-CN/guide/concepts' },
        { text: '最佳实践', link: '/zh-CN/guide/best-practices' },
      ],
    },
    {
      text: '功能',
      items: [
        { text: 'HTTP 代理模式', link: '/zh-CN/guide/http-proxy' },
        { text: 'REST API 与 Redis 协议', link: '/zh-CN/guide/redis-protocol' },
        { text: '配置参考', link: '/zh-CN/guide/configuration' },
        { text: 'API 参考', link: '/zh-CN/guide/api-reference' },
        { text: '对比分析', link: '/zh-CN/guide/diff-table' },
      ],
    },
    {
      text: '关于',
      items: [
        { text: '更新日志', link: '/zh-CN/changelog' },
        { text: '贡献指南', link: '/zh-CN/contributing' },
        { text: '许可证', link: '/zh-CN/license' },
      ],
    },
  ],
}

const footer = {
  message: 'Released under the Apache License 2.0. | 基于 Apache 2.0 协议发布',
  copyright: `Copyright © 2018-${new Date().getFullYear()} fun.commons`,
}

export default defineConfig({
  title: 'retask4j',
  description: 'A Redis-based distributed async task queue with a unique HTTP proxy mode',
  lang: 'en-US',
  base: '/retask4j/',
  lastUpdated: true,
  cleanUrls: true,
  ignoreDeadLinks: true,

  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/retask4j/favicon.svg' }],
    ['meta', { name: 'theme-color', content: '#2563eb' }],
    ['meta', { property: 'og:title', content: 'retask4j' }],
    ['meta', { property: 'og:description', content: 'A Redis-based distributed async task queue with a unique HTTP proxy mode' }],
  ],

  locales: {
    'en': {
      label: 'English',
      lang: 'en-US',
      title: 'retask4j',
      description: 'A Redis-based distributed async task queue with a unique HTTP proxy mode',
      themeConfig: {
        nav: enNav,
        sidebar: enSidebar,
        socialLinks: [{ icon: 'github', link: 'https://github.com/funcommons/retask4j' }],
        footer,
        outline: { level: [2, 3], label: 'On this page' },
        editLink: { pattern: 'https://github.com/funcommons/retask4j/edit/main/site/en/:path', text: 'Edit this page on GitHub' },
        search: { provider: 'local' },
      },
    },
    'zh-CN': {
      label: '简体中文',
      lang: 'zh-CN',
      title: 'retask4j',
      description: '基于 Redis 的分布式异步任务队列框架，独创 HTTP 代理模式',
      themeConfig: {
        nav: zhNav,
        sidebar: zhSidebar,
        socialLinks: [{ icon: 'github', link: 'https://github.com/funcommons/retask4j' }],
        footer,
        outline: { level: [2, 3], label: '本页内容' },
        editLink: { pattern: 'https://github.com/funcommons/retask4j/edit/main/site/zh-CN/:path', text: '在 GitHub 上编辑此页' },
        search: { provider: 'local' },
      },
    },
  },

  themeConfig: {
    nav: enNav,
    sidebar: enSidebar,
    socialLinks: [{ icon: 'github', link: 'https://github.com/funcommons/retask4j' }],
    footer,
    outline: { level: [2, 3], label: 'On this page' },
    editLink: { pattern: 'https://github.com/funcommons/retask4j/edit/main/site/en/:path', text: 'Edit this page on GitHub' },
    search: { provider: 'local' },
  },
})
