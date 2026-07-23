package com.finguard.core.common.exception;

import com.finguard.core.account.exception.AccountNotFoundException;
import com.finguard.core.account.exception.DuplicateAccountNoException;
import com.finguard.core.account.exception.InvalidAccountInputException;
import com.finguard.core.account.exception.InvalidAccountOperationException;
import com.finguard.core.transaction.exception.DuplicateTransactionException;
import com.finguard.core.transaction.exception.InvalidTransactionInputException;
import com.finguard.core.transaction.exception.InvalidTransactionOperationException;
import com.finguard.core.transaction.exception.TransactionNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleAccountNotFound(
            AccountNotFoundException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                ErrorCode.ACCOUNT_NOT_FOUND,
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleTransactionNotFound(
            TransactionNotFoundException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                ErrorCode.TRANSACTION_NOT_FOUND,
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(DuplicateAccountNoException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateAccountNo(
            DuplicateAccountNoException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                ErrorCode.DUPLICATE_ACCOUNT_NO,
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ApiErrorResponse> handleDuplicateTransaction(
            DuplicateTransactionException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                ErrorCode.DUPLICATE_TRANSACTION,
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(InvalidAccountInputException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAccountInput(
            InvalidAccountInputException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_ACCOUNT_INPUT,
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(InvalidTransactionInputException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransactionInput(
            InvalidTransactionInputException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_TRANSACTION_INPUT,
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(InvalidAccountOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidAccountOperation(
            InvalidAccountOperationException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                ErrorCode.INVALID_ACCOUNT_OPERATION,
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(InvalidTransactionOperationException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidTransactionOperation(
            InvalidTransactionOperationException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.CONFLICT,
                ErrorCode.INVALID_TRANSACTION_OPERATION,
                exception.getMessage(),
                request,
                List.of()
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = exception
                .getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fieldError -> new FieldValidationError(
                        fieldError.getField(),
                        fieldError.getDefaultMessage() == null
                                ? "Invalid value"
                                : fieldError.getDefaultMessage()
                ))
                .sorted(Comparator.comparing(
                        FieldValidationError::field
                ))
                .toList();

        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = exception
                .getConstraintViolations()
                .stream()
                .map(this::toFieldError)
                .sorted(Comparator.comparing(
                        FieldValidationError::field
                ))
                .toList();

        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        FieldValidationError fieldError = new FieldValidationError(
                exception.getName(),
                "Invalid value"
        );
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Request parameter has an invalid value",
                request,
                List.of(fieldError)
        );
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request) {
        List<FieldValidationError> fieldErrors = exception
                .getParameterValidationResults()
                .stream()
                .flatMap(result -> {
                    String parameterName = result
                            .getMethodParameter()
                            .getParameterName();
                    String field = parameterName == null
                            ? "parameter"
                            : parameterName;
                    return result.getResolvableErrors()
                            .stream()
                            .map(error -> new FieldValidationError(
                                    field,
                                    error.getDefaultMessage() == null
                                            ? "Invalid value"
                                            : error.getDefaultMessage()
                            ));
                })
                .sorted(Comparator.comparing(
                        FieldValidationError::field
                ))
                .toList();

        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.VALIDATION_FAILED,
                "Request validation failed",
                request,
                fieldErrors
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableMessage(
            HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.BAD_REQUEST,
                ErrorCode.INVALID_REQUEST,
                "Request body is malformed or contains an invalid value",
                request,
                List.of()
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException exception,
            HttpServletRequest request) {
        LOGGER.warn(
                "Data integrity conflict while handling {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );
        return error(
                HttpStatus.CONFLICT,
                ErrorCode.DATA_INTEGRITY_CONFLICT,
                "Request conflicts with existing data",
                request,
                List.of()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
            NoResourceFoundException exception,
            HttpServletRequest request) {
        return error(
                HttpStatus.NOT_FOUND,
                ErrorCode.RESOURCE_NOT_FOUND,
                "Requested resource was not found",
                request,
                List.of()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpectedException(
            Exception exception,
            HttpServletRequest request) {
        LOGGER.error(
                "Unexpected error while handling {} {}",
                request.getMethod(),
                request.getRequestURI(),
                exception
        );
        return error(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ErrorCode.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred",
                request,
                List.of()
        );
    }

    private FieldValidationError toFieldError(
            ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath().toString();
        int separatorIndex = propertyPath.lastIndexOf('.');
        String field = separatorIndex < 0
                ? propertyPath
                : propertyPath.substring(separatorIndex + 1);
        return new FieldValidationError(field, violation.getMessage());
    }

    private ResponseEntity<ApiErrorResponse> error(
            HttpStatus status,
            ErrorCode code,
            String message,
            HttpServletRequest request,
            List<FieldValidationError> fieldErrors) {
        ApiErrorResponse response = new ApiErrorResponse(
                Instant.now(),
                status.value(),
                code,
                message,
                request.getRequestURI(),
                fieldErrors
        );
        return ResponseEntity.status(status).body(response);
    }
}
