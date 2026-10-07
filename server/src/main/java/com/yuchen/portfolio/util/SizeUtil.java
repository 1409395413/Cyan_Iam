package com.yuchen.portfolio.util;

public final class SizeUtil {

  private SizeUtil() {}

  public static String human(long bytes) {
    if (bytes < 1024) return bytes + " B";
    long kb = bytes / 1024;
    if (kb < 1024) return kb + " KB";
    long mb = kb / 1024;
    if (mb < 1024) return mb + " MB";
    return (mb / 1024) + " GB";
  }
}
