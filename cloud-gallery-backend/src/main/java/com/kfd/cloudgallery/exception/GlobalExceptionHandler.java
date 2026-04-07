package com.kfd.cloudgallery.exception;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.kfd.cloudgallery.common.BaseResponse;
import com.kfd.cloudgallery.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(NotLoginException.class)
    public BaseResponse<?> notLoginException(NotLoginException e) {
        log.error("NotLoginException", e);
        return ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, e.getMessage());
    }

    @ExceptionHandler(NotPermissionException.class)
    public BaseResponse<?> notPermissionExceptionHandler(NotPermissionException e) {
        log.error("NotPermissionException", e);
        return ResultUtils.error(ErrorCode.NO_AUTH_ERROR, e.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse<?> businessExceptionHandler(BusinessException e) {
        log.error("BusinessException", e);
        return ResultUtils.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(JsonParseException.class)
    public BaseResponse<?> jsonParseExceptionHandler(JsonParseException e) {
        log.error("JSON解析异常", e);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "JSON格式错误，请检查请求数据");
    }

    @ExceptionHandler(JsonMappingException.class)
    public BaseResponse<?> jsonMappingExceptionHandler(JsonMappingException e) {
        log.error("JSON映射异常", e);
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "JSON数据映射错误，请检查请求参数");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse<?> httpMessageNotReadableExceptionHandler(HttpMessageNotReadableException e) {
        log.error("HTTP消息读取异常", e);
        String message = e.getMessage();
        if (message != null && message.contains("Invalid UTF-8")) {
            return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求数据包含无效字符，请检查输入内容");
        }
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求数据格式错误，请检查输入内容");
    }

    @ExceptionHandler(RuntimeException.class)
    public BaseResponse<?> businessExceptionHandler(RuntimeException e) {
        log.error("RuntimeException", e);
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误");
    }
}
