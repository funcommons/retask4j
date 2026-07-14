/**
 * Post-build script: ensure the site root (`/retask4j/`) redirects visitors to
 * the default locale (`/retask4j/en/`).
 *
 * VitePress with i18n generates a `404.html` at the build root that GitHub Pages
 * uses when no `index.html` is present. We:
 *   1. Replace the build root's 404.html with a tiny redirect HTML.
 *   2. Add a real `index.html` that performs a smarter redirect using the
 *      browser's `Accept-Language` header.
 */
import { readFileSync, writeFileSync, copyFileSync, existsSync } from 'node:fs'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const distDir = join(__dirname, '..', '.vitepress', 'dist')
const defaultLocale = 'en'
const basePath = '/retask4j'

// 1) Replace the build root's 404.html with a redirect page.
//    GitHub Pages serves 404.html when the URL is not found, so this acts as
//    a fallback for users who visit `/retask4j/some-broken-path`.
const redirect404 = `<!DOCTYPE html>
<html lang="en-US">
<head>
<meta charset="utf-8">
<title>retask4j</title>
<meta http-equiv="refresh" content="0; url=${basePath}/${defaultLocale}/">
<link rel="canonical" href="${basePath}/${defaultLocale}/">
</head>
<body><p>Redirecting to <a href="${basePath}/${defaultLocale}/">${basePath}/${defaultLocale}/</a>…</p></body>
</html>
`
const notFoundPath = join(distDir, '404.html')
if (existsSync(notFoundPath)) {
  writeFileSync(notFoundPath, redirect404, 'utf-8')
  console.log(`✓ Rewrote ${notFoundPath} as redirect to ${basePath}/${defaultLocale}/`)
}

// 2) Add a real index.html at the build root that picks the best locale based
//    on the browser's Accept-Language header. Falls back to the default locale
//    for any unmatched or empty value.
const smartIndex = `<!DOCTYPE html>
<html lang="en-US">
<head>
<meta charset="utf-8">
<title>retask4j</title>
<meta http-equiv="refresh" content="0; url=${basePath}/${defaultLocale}/">
<link rel="canonical" href="${basePath}/${defaultLocale}/">
<script>
  (function () {
    var base = '${basePath}';
    var fallback = '/' + '${defaultLocale}' + '/';
    var supported = { en: 1, 'zh-CN': 1 };
    var raw = (navigator.languages && navigator.languages.length)
      ? navigator.languages.join(',')
      : (navigator.language || '');
    var langs = raw.split(',').map(function (s) {
      var i = s.indexOf(';');
      var tag = (i >= 0 ? s.substring(0, i) : s).trim();
      var dash = tag.indexOf('-');
      return (dash >= 0 ? tag.substring(0, dash) : tag).toLowerCase();
    });
    var region = raw.split(',')[0] || '';
    var target = null;
    for (var i = 0; i < langs.length; i++) {
      if (langs[i] === 'zh' && /(^|,)zh-(CN|cn|Hans|cn-Hans)/i.test(raw)) {
        target = '/zh-CN/';
        break;
      }
      if (langs[i] === 'en') { target = '/en/'; break; }
      if (supported[langs[i]]) { target = '/' + langs[i] + '/'; break; }
    }
    if (target) {
      document.querySelector('meta[http-equiv="refresh"]').setAttribute('content', '0; url=' + base + target);
      var link = document.querySelector('link[rel="canonical"]');
      if (link) link.setAttribute('href', base + target);
    }
  })();
</script>
</head>
<body>
<p>Redirecting to <a id="lnk" href="${basePath}/${defaultLocale}/">${basePath}/${defaultLocale}/</a>…</p>
<script>document.getElementById('lnk').href = '${basePath}/${defaultLocale}/';</script>
</body>
</html>
`
const indexPath = join(distDir, 'index.html')
writeFileSync(indexPath, smartIndex, 'utf-8')
console.log(`✓ Wrote ${indexPath} with Accept-Language aware redirect`)
