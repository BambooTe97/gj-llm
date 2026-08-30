/**
 * 聊天消息 Markdown 渲染管线
 *
 * LLM 返回的正文是 Markdown 文本，直接 {{ }} 插值会原样显示井号/星号，
 * 这里统一渲染成 HTML：markdown-it 解析 -> highlight.js 代码高亮 -> DOMPurify 消毒。
 *
 * 安全说明：本项目是 RAG 系统，回答内容混有外部上传文档的文本，
 * 模型可能原样吐出其中的 HTML / <script>，因此：
 * 1. markdown-it 关闭 html 选项（原始 HTML 一律转义）；
 * 2. 渲染结果再过一遍 DOMPurify 作为第二道防线。
 */
import MarkdownIt from 'markdown-it'
import type Token from 'markdown-it/lib/token'
import DOMPurify from 'dompurify'
import hljs from 'highlight.js/lib/core'
// 按需注册常用语言（全量引入 highlight.js 约 900KB，按需注册只占几十 KB；
// 各语言的别名如 js/html/sh 由语言定义自带，注册时自动生效）
import javascript from 'highlight.js/lib/languages/javascript'
import typescript from 'highlight.js/lib/languages/typescript'
import xml from 'highlight.js/lib/languages/xml'
import css from 'highlight.js/lib/languages/css'
import scss from 'highlight.js/lib/languages/scss'
import json from 'highlight.js/lib/languages/json'
import python from 'highlight.js/lib/languages/python'
import java from 'highlight.js/lib/languages/java'
import bash from 'highlight.js/lib/languages/bash'
import sql from 'highlight.js/lib/languages/sql'
import yaml from 'highlight.js/lib/languages/yaml'
import markdown from 'highlight.js/lib/languages/markdown'
import c from 'highlight.js/lib/languages/c'
import cpp from 'highlight.js/lib/languages/cpp'
import csharp from 'highlight.js/lib/languages/csharp'
import go from 'highlight.js/lib/languages/go'
import php from 'highlight.js/lib/languages/php'
import ruby from 'highlight.js/lib/languages/ruby'
import rust from 'highlight.js/lib/languages/rust'
import kotlin from 'highlight.js/lib/languages/kotlin'
import swift from 'highlight.js/lib/languages/swift'
import dart from 'highlight.js/lib/languages/dart'
import dockerfile from 'highlight.js/lib/languages/dockerfile'
import ini from 'highlight.js/lib/languages/ini'
import diff from 'highlight.js/lib/languages/diff'
import powershell from 'highlight.js/lib/languages/powershell'

const LANGUAGES = {
  javascript,
  typescript,
  xml,
  css,
  scss,
  json,
  python,
  java,
  bash,
  sql,
  yaml,
  markdown,
  c,
  cpp,
  csharp,
  go,
  php,
  ruby,
  rust,
  kotlin,
  swift,
  dart,
  dockerfile,
  ini,
  diff,
  powershell,
}
Object.entries(LANGUAGES).forEach(([name, def]) => hljs.registerLanguage(name, def))

const md = new MarkdownIt({
  html: false, // 原始 HTML 一律转义，防止文档/模型输出注入
  linkify: true, // 裸链接自动转 <a>
  breaks: true, // 单换行转 <br>，符合聊天场景的直觉
})

installCitationRule(md)

/** 转义后拼进 HTML 的字符串（语言名来自模型输出，同样不可信） */
function escapeHtml(s: string): string {
  return md.utils.escapeHtml(s)
}

/**
 * 代码块渲染：包一层 .md-code 容器，
 * 带语言标签栏 + 复制按钮（复制按钮通过 data-code-copy 标记，由外层事件委托处理）
 */
md.renderer.rules.fence = (tokens, idx) => {
  const token = tokens[idx]
  const lang = (token.info || '').trim().split(/\s+/)[0]
  const code = token.content

  let highlighted = ''
  if (lang && hljs.getLanguage(lang)) {
    try {
      highlighted = hljs.highlight(code, { language: lang, ignoreIllegals: true }).value
    } catch {
      // 高亮失败则回退为纯文本
    }
  }
  if (!highlighted) {
    highlighted = escapeHtml(code)
  }

  const langLabel = escapeHtml(lang || 'text')
  return (
    `<div class="md-code">` +
    `<div class="md-code__header">` +
    `<span class="md-code__lang">${langLabel}</span>` +
    `<button class="md-code__copy" type="button" data-code-copy>复制</button>` +
    `</div>` +
    `<pre><code class="hljs">${highlighted}</code></pre>` +
    `</div>\n`
  )
}

/** 链接统一新标签页打开，并加安全 rel */
const defaultLinkOpen =
  md.renderer.rules.link_open ||
  ((tokens, idx, options, _env, self) => self.renderToken(tokens, idx, options))

function setTokenAttr(token: { attrIndex(s: string): number; attrPush(a: [string, string]): void; attrs: [string, string][] | null }, name: string, value: string) {
  const i = token.attrIndex(name)
  // attrPush 在 attrs 为 null 时会自动初始化
  if (i < 0 || !token.attrs) token.attrPush([name, value])
  else token.attrs[i][1] = value
}

md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  setTokenAttr(tokens[idx], 'target', '_blank')
  setTokenAttr(tokens[idx], 'rel', 'noopener noreferrer nofollow')
  return defaultLinkOpen(tokens, idx, options, env, self)
}

/**
 * 行内引用角标：把回答正文中的 [n]（n ≤ maxCite）转为上标
 * &lt;sup class="md-cite" data-cite="n"&gt;，供 ChatMessage 事件委托锚定到参考来源面板。
 *
 * - 超出编号范围的（模型幻觉出的不存在的编号）保持纯文本原样显示
 * - 仅处理文本 token，代码块 / 行内代码中的 [1] 不转换
 * - n 由正则捕获为纯数字，模板串无注入面；DOMPurify 默认放行 data-* 属性
 */
function installCitationRule(md: MarkdownIt) {
  md.core.ruler.push('gj_citation', (state) => {
    const maxCite = (state.env as { maxCite?: number } | undefined)?.maxCite
    if (!maxCite || maxCite <= 0) return
    for (const blockToken of state.tokens) {
      if (blockToken.type !== 'inline' || !blockToken.children) continue
      const children = blockToken.children
      const rewritten: Token[] = []
      let changed = false
      for (const t of children) {
        if (t.type === 'text' && t.content.includes('[')) {
          const parts = splitCitations(t, maxCite)
          if (parts !== null) {
            rewritten.push(...parts)
            changed = true
            continue
          }
        }
        rewritten.push(t)
      }
      if (changed) blockToken.children = rewritten
    }
  })
}

/** 把含 [n] 的文本 token 切分为 文本/角标 序列；无有效角标时返回 null（保留原 token） */
function splitCitations(token: Token, maxCite: number): Token[] | null {
  const content = token.content
  const re = /\[(\d{1,3})\]/g
  const parts: Token[] = []
  let last = 0
  let m: RegExpExecArray | null
  while ((m = re.exec(content)) !== null) {
    const n = Number(m[1])
    if (n < 1 || n > maxCite) continue
    if (m.index > last) parts.push(textToken(content.slice(last, m.index)))
    parts.push(citeToken(n))
    last = m.index + m[0].length
  }
  if (parts.length === 0) return null
  if (last < content.length) parts.push(textToken(content.slice(last)))
  return parts
}

function textToken(content: string): Token {
  const t = new Token('text', '', 0)
  t.content = content
  return t
}

function citeToken(n: number): Token {
  const t = new Token('html_inline', '', 0)
  t.content = `<sup class="md-cite" data-cite="${n}">[${n}]</sup>`
  return t
}

/**
 * 流式场景：内容可能刚好截断在未闭合的代码围栏处，
 * 直接渲染会导致后半段全部掉进代码块里“闪跳”。
 * 检测行首围栏（``` 或 ~~~）数量为奇数时，临时补一个闭合围栏，
 * 让代码块边流边稳定显示。
 */
function balanceUnclosedFence(src: string): string {
  const fenceRe = /^[ \t]*(```|~~~)/gm
  let count = 0
  let lastMarker = '```'
  let m: RegExpExecArray | null
  while ((m = fenceRe.exec(src)) !== null) {
    count++
    lastMarker = m[1]
  }
  return count % 2 === 1 ? src + '\n' + lastMarker : src
}

/**
 * 渲染 Markdown 为可安全插入 v-html 的 HTML
 * @param src Markdown 原文
 * @param opts.streaming 流式传输中（容忍未闭合围栏等半截语法）
 * @param opts.maxCite 引用片段数量上限（[n] 角标只转换 n ≤ maxCite 的，防模型幻觉编号）
 */
export function renderMarkdown(src: string, opts?: { streaming?: boolean; maxCite?: number }): string {
  if (!src) return ''
  const source = opts?.streaming ? balanceUnclosedFence(src) : src
  const html = md.render(source, { maxCite: opts?.maxCite })
  return DOMPurify.sanitize(html, {
    ADD_ATTR: ['target', 'rel'], // 链接新标签打开所需属性（默认已允许，显式声明防配置收紧）
  })
}
