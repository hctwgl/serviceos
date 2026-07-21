package com.serviceos.project.api;

import java.util.Collection;
import java.util.Map;

/**
 * 行政区编码 → 显示名只读端口（M406 目录）。
 *
 * <p>供跨模块读模型组装中文摘要；未命中编码时调用方回退到编码本身，不得伪造地名。</p>
 */
public interface RegionCatalogNameQuery {
    Map<String, String> findNames(Collection<String> regionCodes);
}
