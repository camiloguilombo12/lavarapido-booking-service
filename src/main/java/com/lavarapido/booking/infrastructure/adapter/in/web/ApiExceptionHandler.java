package com.lavarapido.booking.infrastructure.adapter.in.web;

import com.lavarapido.booking.domain.exception.ConflictException;
import com.lavarapido.booking.domain.exception.DependencyUnavailableException;
import com.lavarapido.booking.domain.exception.DomainException;
import com.lavarapido.booking.domain.exception.ForbiddenException;
import com.lavarapido.booking.domain.exception.NotFoundException;
import com.lavarapido.booking.domain.exception.ScheduleConflictException;
import com.lavarapido.booking.domain.exception.SlotUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.dao.UncategorizedDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Todos los errores salen como application/problem+json con un code fijo que el frontend
 * traduce (API_ERRORS.<code>). Los detalles internos nunca llegan al cliente.
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * RF-006: la hora no esta libre; van las alternativas del mismo dia para que el cliente escoja y
     * el motivo (reason: CLOSED_DAY, OUTSIDE_HOURS, TIME_PASSED o NO_FREE_BAY).
     */
    @ExceptionHandler(SlotUnavailableException.class)
    ProblemDetail handleSlotUnavailable(SlotUnavailableException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, exception.code(), exception.getMessage());
        problem.setProperty("alternatives", exception.alternatives().stream().map(HOUR::format).toList());
        problem.setProperty("reason", exception.reason().name());
        return problem;
    }

    /**
     * BIZ-10: el cambio de horario, de excepcion o de bahia deja reservas por fuera. Van cuantas son
     * (conflictingBookings) y los codigos de las primeras (bookingCodes) para que el admin las ubique.
     */
    @ExceptionHandler(ScheduleConflictException.class)
    ProblemDetail handleScheduleConflict(ScheduleConflictException exception) {
        ProblemDetail problem = problem(HttpStatus.CONFLICT, exception.code(), exception.getMessage());
        problem.setProperty("conflictingBookings", exception.conflictingBookings());
        problem.setProperty("bookingCodes", exception.bookingCodes());
        return problem;
    }

    @ExceptionHandler(DomainException.class)
    ProblemDetail handleDomain(DomainException exception) {
        return problem(statusOf(exception), exception.code(), exception.getMessage());
    }

    /**
     * BIZ-09 / E-11: otra peticion cambio la reserva entre la lectura y el guardado (row_version,
     * @Version). Su cambio no se pisa: 409 y quien llama vuelve a cargarla.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail handleStaleUpdate(OptimisticLockingFailureException exception) {
        log.info("Stale update rejected: {}", exception.getMessage());
        return problem(HttpStatus.CONFLICT, "CONFLICT", "The record was changed by someone else, load it again");
    }

    /**
     * BIZ-13 / E-18: la base no responde (caida, sin conexiones, tiempo agotado). Es 503 con el code
     * que la web y la app ya traducen ("el servicio de reservas no responde"), no 409.
     */
    @ExceptionHandler({DataAccessResourceFailureException.class, TransientDataAccessResourceException.class,
            QueryTimeoutException.class, RecoverableDataAccessException.class, CannotCreateTransactionException.class})
    ProblemDetail handleDatabaseUnavailable(Exception exception) {
        log.error("Database unavailable: {}", exception.getMessage());
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "BOOKING_SERVICE_UNAVAILABLE",
                "The database is not responding, try again in a moment");
    }

    /**
     * Los indices unicos, los CHECK y los triggers de la base son la ultima red (por ejemplo, dos
     * reservas cruzadas en la misma bahia, tr_booking_bay_overlap, que llega como error sin categoria).
     * Un bloqueo que no se pudo tomar tambien es un choque. Si saltan es 409, no 500.
     */
    @ExceptionHandler({DataIntegrityViolationException.class, ConcurrencyFailureException.class,
            UncategorizedDataAccessException.class})
    ProblemDetail handleDataConflict(DataAccessException exception) {
        log.warn("Database rejected the change: {}", exception.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "CONFLICT", "The data conflicts with an existing record");
    }

    /** BIZ-13: cualquier otro error de la base (SQL mal armado, mal uso de la API) es nuestro: 500, no 409. */
    @ExceptionHandler(DataAccessException.class)
    ProblemDetail handleDataError(DataAccessException exception) {
        log.error("Database error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException exception) {
        return problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Parameter " + exception.getName() + " is invalid");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unexpected error", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "The request has invalid fields");
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    /** Los errores de Spring MVC (JSON mal formado, metodo incorrecto) tambien llevan code. */
    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers, HttpStatusCode statusCode,
                                                          WebRequest request) {
        if (body instanceof ProblemDetail problem && problem.getProperties() == null) {
            problem.setProperty("code", HttpStatus.valueOf(statusCode.value()).name());
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private static HttpStatus statusOf(DomainException exception) {
        return switch (exception) {
            // no existe, o es de otro cliente: 404 en los dos casos para no revelar que existe
            case NotFoundException ignored -> HttpStatus.NOT_FOUND;
            case ConflictException ignored -> HttpStatus.CONFLICT;
            case DependencyUnavailableException ignored -> HttpStatus.SERVICE_UNAVAILABLE;
            // BIZ-13: customer contesto que no hay permiso; respondio, asi que no es 503
            case ForbiddenException ignored -> HttpStatus.FORBIDDEN;
            default -> HttpStatus.BAD_REQUEST;
        };
    }

    private static ProblemDetail problem(HttpStatus status, String code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        return problem;
    }
}
