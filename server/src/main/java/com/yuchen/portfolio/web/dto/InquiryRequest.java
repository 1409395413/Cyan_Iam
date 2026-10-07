package com.yuchen.portfolio.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class InquiryRequest {

  @NotBlank(message = "请填写称呼")
  @Size(max = 64)
  private String name;

  @NotBlank(message = "请填写联系方式")
  @Size(max = 128)
  private String contact;

  @Size(max = 64)
  private String type;

  /** 预算区间，选填 */
  @Size(max = 64)
  private String budget;

  @NotBlank(message = "请简单描述一下项目")
  @Size(max = 2000)
  private String message;

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getContact() { return contact; }
  public void setContact(String contact) { this.contact = contact; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public String getBudget() { return budget; }
  public void setBudget(String budget) { this.budget = budget; }
  public String getMessage() { return message; }
  public void setMessage(String message) { this.message = message; }
}
