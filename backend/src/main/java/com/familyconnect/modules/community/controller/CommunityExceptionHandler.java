package com.familyconnect.modules.community.controller;

import com.familyconnect.modules.community.dto.CommunityDto.*;
import com.familyconnect.modules.community.service.CommunityFailure;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@RestControllerAdvice(basePackageClasses = CommunityController.class)
public class CommunityExceptionHandler {
    private ResponseEntity<ErrorResponse> error(HttpStatus status, String code,
                                                 String message, List<ErrorDetail> details) {
        return ResponseEntity.status(status)
            .body(new ErrorResponse(false,new ErrorInfo(code,message,details)));
    }
    @ExceptionHandler(CommunityFailure.class)
    public ResponseEntity<ErrorResponse> failed(CommunityFailure ex) {
        var detail = ex.field() == null ? List.<ErrorDetail>of() :
            List.of(new ErrorDetail(ex.field(),ex.getMessage()));
        return error(ex.status(),ex.code(),ex.getMessage(),detail);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> invalid(MethodArgumentNotValidException ex) {
        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new ErrorDetail(e.getField(),e.getDefaultMessage())).toList();
        return error(HttpStatus.BAD_REQUEST,"VALID_001","Dữ liệu không hợp lệ",details);
    }
    @ExceptionHandler({HttpMessageNotReadableException.class,MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> invalidRequest(Exception ex) {
        return error(HttpStatus.BAD_REQUEST,"VALID_001","Dữ liệu không hợp lệ",List.of());
    }
}
