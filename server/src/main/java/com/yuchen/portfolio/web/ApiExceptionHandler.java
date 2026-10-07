package com.yuchen.portfolio.web;

import com.yuchen.portfolio.util.SizeUtil;
import com.yuchen.portfolio.web.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/** 全局异常处理：对外一律 JSON，不泄漏堆栈。 */
@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ApiException.class)
  public ResponseEntity<ErrorResponse> handleApi(ApiException e) {
    return status(e.getStatus(), e.getCode(), e.getMessage());
  }

  @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
  public ResponseEntity<ErrorResponse> handleValidation(Exception e) {
    String msg = "参数校验失败";
    if (e instanceof MethodArgumentNotValidException m) {
      var fe = m.getBindingResult().getFieldError();
      if (fe != null) msg = fe.getDefaultMessage();
    } else if (e instanceof ConstraintViolationException c) {
      var v = c.getConstraintViolations().stream().findFirst();
      if (v.isPresent()) msg = v.get().getMessage();
    }
    return status(400, "BAD_REQUEST", msg);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleBadJson() {
    return status(400, "BAD_REQUEST", "请求体不是合法 JSON");
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<ErrorResponse> handleTooBig(MaxUploadSizeExceededException e) {
    return status(413, "PAYLOAD_TOO_LARGE", "上传文件过大");
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorResponse> handleDenied() {
    return status(403, "FORBIDDEN", "没有权限执行此操作");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleOther(Exception e) {
    log.error("unhandled exception", e);
    return status(500, "INTERNAL_ERROR", "服务器内部错误");
  }

  private ResponseEntity<ErrorResponse> status(int httpStatus, String code, String message) {
    return ResponseEntity.status(HttpStatus.valueOf(httpStatus))
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ErrorResponse(code, message));
  }
}
