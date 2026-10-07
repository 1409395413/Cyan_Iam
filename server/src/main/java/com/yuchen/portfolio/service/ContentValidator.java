package com.yuchen.portfolio.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yuchen.portfolio.util.HtmlSanitizer;
import com.yuchen.portfolio.web.ApiException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 站点内容的合法性校验与净化。
 *
 * <p>后端刻意 <b>不解析</b> 模块语义，只做结构安全校验 ——
 * 这样新增任意模块类型都不需要改后端。但要保证：
 * <ul>
 *   <li>整体是合法 JSON 且体积可控（防炸弹）</li>
 *   <li>modules 是数组、每个元素有合法的 id / type，且 id 不重复（渲染层依赖 id 作锚点）</li>
 *   <li>富文本字段过 HTML 白名单（防 XSS）</li>
 *   <li>src / href / poster 只能是安全协议（防 javascript: 伪协议）</li>
 * </ul>
 */
@Component
public class ContentValidator {

  private static final long MAX_PAYLOAD_BYTES = 4L * 1024 * 1024;   // 4MB
  private static final int MAX_MODULES = 300;
  private static final int MAX_NODES = 30_000;
  private static final int MAX_STRING_LEN = 20_000;
  private static final Pattern MODULE_ID = Pattern.compile("^[A-Za-z0-9_-]{1,64}$");

  /** 允许内联 HTML 的字段，其余字符串一律当作纯文本。 */
  private static final Set<String> RICH_TEXT_KEYS = Set.of(
      "title", "lead", "body", "eyebrow", "desc", "description",
      "formNote", "submitLabel", "footerNote", "copyright", "note", "caption", "cap", "cap2");

  /** 视为 URL 的字段。 */
  private static final Set<String> URL_KEYS = Set.of("src", "href", "poster", "link", "url");

  private int nodeCount = 0;

  public JsonNode validateAndClean(JsonNode root) {
    nodeCount = 0;
    if (root == null || !root.isObject()) {
      throw ApiException.badRequest("内容必须是 JSON 对象");
    }
    if (estimateSize(root) > MAX_PAYLOAD_BYTES) {
      throw ApiException.badRequest("内容超过 4MB 上限");
    }

    ObjectNode out = walk((ObjectNode) deepCopy(root));

    JsonNode site = out.get("site");
    if (site == null || !site.isObject()) {
      throw ApiException.badRequest("缺少 site 配置对象");
    }

    JsonNode modules = out.get("modules");
    if (modules == null || !modules.isArray()) {
      throw ApiException.badRequest("modules 必须是数组");
    }
    if (modules.size() > MAX_MODULES) {
      throw ApiException.badRequest("模块数量超过 " + MAX_MODULES + " 上限");
    }

    Set<String> seenIds = new HashSet<>();
    for (JsonNode m : modules) {
      if (!m.isObject()) throw ApiException.badRequest("模块必须是对象");
      JsonNode id = m.get("id");
      JsonNode type = m.get("type");
      if (id == null || !MODULE_ID.matcher(id.asText()).matches()) {
        throw ApiException.badRequest("模块 id 非法：只能包含字母、数字、下划线、连字符");
      }
      if (!seenIds.add(id.asText())) {
        throw ApiException.badRequest("模块 id 重复：" + id.asText());
      }
      if (type == null || type.asText().isBlank() || type.asText().length() > 32) {
        throw ApiException.badRequest("模块缺少 type 或 type 过长");
      }
    }
    return out;
  }

  /** 递归复制并处理字符串节点，同时统计节点数防止恶意膨胀。 */
  private JsonNode deepCopy(JsonNode n) {
    if (++nodeCount > MAX_NODES) {
      throw ApiException.badRequest("内容节点过多，请精简后再保存");
    }
    return n.deepCopy();
  }

  private ObjectNode walk(ObjectNode node) {
    Iterator<Map.Entry<String, JsonNode>> it = node.fields();
    while (it.hasNext()) {
      Map.Entry<String, JsonNode> e = it.next();
      String key = e.getKey();
      JsonNode v = e.getValue();

      if (v.isObject()) {
        walk((ObjectNode) v);
      } else if (v.isArray()) {
        walkArray(key, (com.fasterxml.jackson.databind.node.ArrayNode) v);
      } else if (v.isTextual()) {
        String raw = v.asText();
        if (raw.length() > MAX_STRING_LEN) {
          node.put(key, raw.substring(0, MAX_STRING_LEN));
          continue;
        }
        if (RICH_TEXT_KEYS.contains(key)) {
          node.put(key, HtmlSanitizer.clean(raw));
        } else if (URL_KEYS.contains(key)) {
          node.put(key, safeUrl(raw));
        }
      }
    }
    return node;
  }

  private void walkArray(String fieldKey, com.fasterxml.jackson.databind.node.ArrayNode arr) {
    // skills / chips / projectTypes 这类纯字符串数组走一遍净化，防止 <script> 混入
    for (int i = 0; i < arr.size(); i++) {
      JsonNode v = arr.get(i);
      if (v.isObject()) {
        walk((ObjectNode) v);
      } else if (v.isArray()) {
        walkArray(fieldKey, (com.fasterxml.jackson.databind.node.ArrayNode) v);
      } else if (v.isTextual()) {
        String raw = v.asText();
        if (raw.length() > MAX_STRING_LEN) {
          arr.set(i, com.fasterxml.jackson.databind.node.TextNode.valueOf(
              raw.substring(0, MAX_STRING_LEN)));
        } else if (RICH_TEXT_KEYS.contains(fieldKey)) {
          arr.set(i, com.fasterxml.jackson.databind.node.TextNode.valueOf(
              HtmlSanitizer.clean(raw)));
        } else if (URL_KEYS.contains(fieldKey)) {
          arr.set(i, com.fasterxml.jackson.databind.node.TextNode.valueOf(safeUrl(raw)));
        }
      }
    }
  }

  /** 只允许 http(s) / mailto / tel / 站内相对路径 / data:image。 */
  private String safeUrl(String raw) {
    String s = raw == null ? "" : raw.trim();
    if (s.isEmpty()) return "";
    String lower = s.toLowerCase();
    if (lower.startsWith("https://") || lower.startsWith("http://")
        || lower.startsWith("mailto:") || lower.startsWith("tel:")
        || lower.startsWith("data:image/")
        || lower.startsWith("/") || lower.startsWith("#") || lower.startsWith("./")) {
      return s;
    }
    return "";
  }

  private long estimateSize(JsonNode n) {
    return n.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
  }
}
