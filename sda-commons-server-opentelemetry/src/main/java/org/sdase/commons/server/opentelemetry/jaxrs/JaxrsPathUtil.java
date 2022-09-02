package org.sdase.commons.server.opentelemetry.jaxrs;

public final class JaxrsPathUtil {
  private JaxrsPathUtil() {}

  public static String normalizePath(String path) {
    // ensure that non-empty path starts with /
    if (path == null || "/".equals(path)) {
      path = "";
    } else if (!path.startsWith("/")) {
      path = "/" + path;
    }
    // remove trailing /
    if (path.endsWith("/")) {
      path = path.substring(0, path.length() - 1);
    }

    return path;
  }
}
