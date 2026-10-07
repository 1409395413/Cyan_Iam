package com.yuchen.portfolio.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChangePasswordRequest {

  @NotBlank
  private String oldPassword;

  @NotBlank
  @Size(min = 8, max = 128, message = "新密码至少 8 位")
  private String newPassword;

  public String getOldPassword() { return oldPassword; }
  public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
  public String getNewPassword() { return newPassword; }
  public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
}
