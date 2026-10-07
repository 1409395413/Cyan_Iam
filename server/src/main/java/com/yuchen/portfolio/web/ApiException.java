package com.yuchen.portfolio.web;

/** 业务异常：对外只暴露 code / message，不抛堆栈。 */
public class ApiException extends RuntimeException {

  private final int status;
  private final String code;

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public static ApiException badRequest(String message) {
    return new ApiException(400, "BAD_REQUEST", message);
  }

  public static ApiException unauthorized(String message) {
    return new ApiException(401, "UNAUTHORIZED", message);
  }

  public static ApiException forbidden(String message) {
    return new ApiException(403, "FORBIDDEN", message);
  }

  public static ApiException notFound(String message) {
    return new ApiException(404, "NOT_FOUND", message);
  }

  public static ApiException tooManyRequests(String message) {
    return new ApiException(429, "TOO_MANY_REQUESTS", message);
  }

  public int getStatus() { return status; }
  public String getCode() { return code; }
}
