package com.yuchen.portfolio.web.dto;

import jakarta.validation.constraints.NotBlank;

public class LoginRequest {

  @NotBlank(message = "用户名不能为空")
  private String username;

  @NotBlank(message = "密码不能为空")
  private String password;

  private Boolean remember;

  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password; }
  public Boolean getRemember() { return remember; }
  public void setRemember(Boolean remember) { this.remember = remember; }
}
