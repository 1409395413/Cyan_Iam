package com.yuchen.portfolio.web.dto;

/** 统一错误响应体，与前端 <code>ApiError</code> 类型一致。 */
public class ErrorResponse {

  private String code;
  private String message;

  public ErrorResponse() {}

  public ErrorResponse(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String getCode() { return code; }
  public void setCode(String code) { this.code = code; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
}
