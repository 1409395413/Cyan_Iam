package com.yuchen.portfolio.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * 管理员输入字符串的 HTML 净化。
 *
 * <p>前端有些字段用 <code>v-html</code> 渲染（标题换行、行内强调），
 * 因此保存时必须过一遍白名单，防止后台账号泄露后被写入 <code>&lt;script&gt;</code>。
 */
public final class HtmlSanitizer {

  private HtmlSanitizer() {}

  /**
   * 站内锚点（href="#photo"）能不能活下来，取决于有没有 baseUri。
   *
   * <p>实测结论（jsoup 1.17.2）：只设 preserveRelativeLinks(true) 不够 —— 没有
   * baseUri 时 absUrl() 解析不出东西，相对链接照样被判为不安全而剥掉 href。
   * 必须给一个 baseUri（这里用保留域名，不会真的发出请求），
   * 输出里保留的仍是原本的相对写法，不会被改写成绝对地址。
   */
  private static final String BASE_URI = "https://portfolio.invalid/";

  private static final Safelist ALLOWED = Safelist.relaxed()
      .addTags("figure", "figcaption", "span", "div", "br")
      .addAttributes(":all", "class", "style", "target", "rel")
      .addProtocols("a", "href", "http", "https", "mailto", "tel", "#")
      .addProtocols("img", "src", "http", "https", "data")
      .preserveRelativeLinks(true);

  /** 允许少量排版标签，去掉 script / iframe / 事件属性 / javascript: 协议。 */
  public static String clean(String html) {
    if (html == null || html.isBlank()) return html;
    return Jsoup.clean(html, BASE_URI, ALLOWED);
  }
}
