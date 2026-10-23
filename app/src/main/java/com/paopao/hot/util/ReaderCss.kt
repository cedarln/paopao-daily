package com.paopao.hot.util

import android.net.Uri

/**
 * 阅读模式样式：各平台移动页顶部都有很高的站点导航和「打开 App」横幅，
 * 在 WebView 里会和正文抢空间。这里按站点注入 CSS，尽量只留正文。
 *
 * 选择器是启发式的，站点改版会失效，但失效只会退化成原页面，不影响功能。
 */
object ReaderCss {

    /**
     * 站点顶栏类名多为构建哈希（如知乎的 css-1ncvp5r），CSS 选择器追不上，
     * 这里用结构特征兜底：贴顶（top≈0）、通栏（宽 ≥ 60% 视口）、矮条（≤ 140px）、
     * 文字很少的 fixed/sticky 元素视为导航/App 引导条并隐藏。正文容器不会命中。
     */
    fun cleanupScript(): String = """
        (function() {
          var HIDDEN = '__paopaoHidden';
          // 知乎等站会在滚动中途弹出「是否在 App 内阅读全文」，点取消后还能继续读；
          // 只认「取消/暂不」且弹窗文案同时含 App 与 阅读/打开，避免误点正文按钮
          function dismissAppModal() {
            var nodes = document.querySelectorAll('button, a, div, span');
            for (var i = 0; i < nodes.length; i++) {
              var el = nodes[i];
              if (el.children.length > 0) continue;
              var t = (el.textContent || '').trim();
              if (t !== '取消' && t !== '暂不' && t !== '以后再说') continue;
              var p = el, around = '', tooLong = false;
              for (var k = 0; k < 6 && p; k++) {
                around = (p.textContent || '').trim();
                if (around.length > 120) { tooLong = true; break; }
                p = p.parentElement;
              }
              if (tooLong || !/App/i.test(around)) continue;
              if (!/阅读|打开/.test(around)) continue;
              el.click();
              // 弹窗打开时站点会把 body 锁成 overflow:hidden，点掉后手动解锁
              document.body.style.overflow = '';
              return true;
            }
            return false;
          }
          function sweep() {
            var nodes = document.querySelectorAll('body *');
            for (var i = 0; i < nodes.length; i++) {
              var el = nodes[i];
              if (el[HIDDEN]) continue;
              var cs = getComputedStyle(el);
              if (cs.position !== 'fixed' && cs.position !== 'sticky') continue;
              var r = el.getBoundingClientRect();
              if (r.height <= 4 || r.height > 140) continue;
              if (r.top > 8) continue;
              if (r.width < window.innerWidth * 0.6) continue;
              if ((el.textContent || '').trim().length > 120) continue;
              el[HIDDEN] = true;
              el.style.setProperty('display', 'none', 'important');
              // 部分站点（知乎）用一个等高的空 div 占据顶栏位置，不同时去掉会留下白带
              var sibs = el.parentElement ? el.parentElement.children : [];
              for (var j = 0; j < sibs.length; j++) {
                var s = sibs[j];
                if (s === el || s[HIDDEN] || s.children.length > 0) continue;
                var sr = s.getBoundingClientRect();
                if (sr.top > 8 || Math.abs(sr.height - r.height) > 4) continue;
                if ((s.textContent || '').trim().length > 0) continue;
                s[HIDDEN] = true;
                s.style.setProperty('display', 'none', 'important');
              }
            }
          }
          function run() { sweep(); dismissAppModal(); }
          run();
          [600, 1500, 3000, 6000].forEach(function(t) { setTimeout(run, t); });
          // 弹窗/顶栏多为滚动后动态插入，观察 60 秒内的 DOM 变化即可，避免常驻开销
          var timer = null;
          var observer = new MutationObserver(function() {
            if (timer) return;
            timer = setTimeout(function() { timer = null; run(); }, 500);
          });
          observer.observe(document.body, { childList: true, subtree: true });
          setTimeout(function() { observer.disconnect(); }, 60000);
        })();
    """.trimIndent()

    fun forUrl(url: String): String {
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase().orEmpty()
        val extra = when {
            host.endsWith("zhihu.com") -> ZHIHU
            host.endsWith("weibo.com") || host.endsWith("weibo.cn") -> WEIBO
            host.endsWith("tieba.baidu.com") -> TIEBA
            host.endsWith("bilibili.com") -> BILIBILI
            host.endsWith("hupu.com") -> HUPU
            else -> ""
        }
        return BASE + extra
    }

    /** 通用：下载/打开 App 的横幅、引导层、浮动按钮 */
    private val BASE = """
        [class*="app-download" i], [id*="app-download" i],
        [class*="download-app" i], [class*="appdownload" i],
        [class*="open-app" i], [class*="openapp" i], [class*="open-in-app" i],
        [class*="app-promotion" i], [class*="apppromotion" i],
        [class*="smartbanner" i], [id*="smartbanner" i],
        [class*="app-guide" i], [class*="download-guide" i],
        [class*="launch-app" i], [class*="call-app" i] {
            display: none !important;
        }
        header[class*="fixed" i], header[class*="sticky" i] {
            display: none !important;
        }
    """.trimIndent()

    private val ZHIHU = """
        .AppHeader, .MobileAppHeader, .AppPromotion, .OpenInAppButton,
        .Modal-wrapper, .MobileModal-wrapper, .Modal-backdrop, .signFlowModal, .AdblockBanner,
        [class*="OpenInApp"], [class*="AppPromotion"], [class*="AppDownload"] {
            display: none !important;
        }
    """.trimIndent()

    private val WEIBO = """
        .m-navbar, .nav-bar, .top-navbar, .woo-header, .gn_header,
        [class*="TopBar"], [class*="top-bar"], [class*="nav-bar"] {
            display: none !important;
        }
    """.trimIndent()

    private val TIEBA = """
        .pb-header, .header-wrap, #tb_header, .top-bar, .search-nav, .pb-nav,
        [class*="head-nav"], [class*="header-wrap"] {
            display: none !important;
        }
    """.trimIndent()

    private val BILIBILI = """
        .bili-header, .mini-header, #biliMainHeader, .international-header {
            display: none !important;
        }
    """.trimIndent()

    private val HUPU = """
        .bbs-header, .hupu-header, .m-header, [class*="header-wrap"] {
            display: none !important;
        }
    """.trimIndent()
}
