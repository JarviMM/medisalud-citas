package com.medisalud.agenda.exception;

import com.medisalud.agenda.dto.ErrorResponse;
import com.medisalud.agenda.dto.ErrorResponse.DetalleCampo;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.TypeMismatchException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce cualquier excepcion en una respuesta {@link ErrorResponse} con el mismo formato.
 *
 * <p><b>Por que extiende {@link ResponseEntityExceptionHandler}:</b> con solo declarar
 * {@code @ExceptionHandler} sueltos se cubren las excepciones que uno recuerda, y el resto
 * de fallos de Spring MVC (metodo no permitido, content-type no soportado, parametro
 * ausente, JSON ilegible) seguirian devolviendo el cuerpo por defecto de Spring, con un
 * formato distinto al nuestro. La clase base ya intercepta todas esas excepciones; al
 * sobrescribir {@link #handleExceptionInternal} se reemplaza su cuerpo de una sola vez y
 * se garantiza que <b>ninguna</b> respuesta de error se escape del contrato.</p>
 *
 * <p><b>Sobre la informacion que se devuelve:</b> ningun handler propaga
 * {@code ex.getMessage()} de excepciones tecnicas. Los mensajes de Jackson, Hibernate o
 * JDBC contienen nombres de clases, de tablas y fragmentos de SQL; devolverlos revela la
 * estructura interna del sistema a un atacante. Los mensajes de esta clase se redactan
 * aqui, y el detalle real se registra en el log del servidor.</p>
 */
@RestControllerAdvice
@RequiredArgsConstructor
@Slf4j
public class ManejadorGlobalDeErrores extends ResponseEntityExceptionHandler {

    private static final String MENSAJE_ERROR_INTERNO =
            "Se produjo un error interno al procesar la petición. Si el problema persiste, "
                    + "contacte con soporte indicando la marca de tiempo de esta respuesta.";

    private final Clock clock;

    // ------------------------------------------------------------------ dominio

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> manejarRecursoNoEncontrado(
            RecursoNoEncontradoException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getCodigo(), ex.getMessage(), null,
                peticion.getRequestURI());
    }

    @ExceptionHandler(ConflictoDeNegocioException.class)
    public ResponseEntity<ErrorResponse> manejarConflictoDeNegocio(
            ConflictoDeNegocioException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.CONFLICT, ex.getCodigo(), ex.getMessage(), null,
                peticion.getRequestURI());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ErrorResponse> manejarSolicitudInvalida(
            SolicitudInvalidaException ex, HttpServletRequest peticion) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getCodigo(), ex.getMessage(), null,
                peticion.getRequestURI());
    }

    // ------------------------------------------------------------- persistencia

    /**
     * Red de seguridad para las restricciones que impone el esquema.
     *
     * <p>Los servicios comprueban las reglas antes de escribir y lanzan
     * {@link ConflictoDeNegocioException} con un mensaje preciso. Este handler solo actua
     * cuando esa comprobacion no llego a tiempo: dos peticiones concurrentes que la
     * superan a la vez y colisionan en el INSERT. El mensaje es generico a proposito,
     * porque el detalle de la restriccion incluye nombres de tablas y columnas.</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> manejarViolacionDeIntegridad(
            DataIntegrityViolationException ex, HttpServletRequest peticion) {
        log.warn("Violación de integridad en {} {}: {}",
                peticion.getMethod(), peticion.getRequestURI(),
                ex.getMostSpecificCause().getMessage());
        return respuesta(HttpStatus.CONFLICT, CodigoError.CONFLICTO_DE_INTEGRIDAD,
                "La operación no se pudo completar porque entra en conflicto con datos ya existentes.",
                null, peticion.getRequestURI());
    }

    // ----------------------------------------------------------------- fallback

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> manejarErrorNoControlado(
            Exception ex, HttpServletRequest peticion) {
        log.error("Error no controlado en {} {}", peticion.getMethod(), peticion.getRequestURI(), ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, CodigoError.ERROR_INTERNO,
                MENSAJE_ERROR_INTERNO, null, peticion.getRequestURI());
    }

    // ------------------------------------------- sobrescrituras de Spring MVC

    /**
     * Fallo de Bean Validation sobre el cuerpo de la peticion: 400 con detalle por campo.
     *
     * <p>Los detalles se ordenan por nombre de campo para que la respuesta sea
     * reproducible; el orden en que Hibernate Validator evalua las restricciones no esta
     * garantizado, y sin ordenar, un test que compare la respuesta completa seria
     * intermitente.</p>
     */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<DetalleCampo> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new DetalleCampo(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(DetalleCampo::campo)
                        .thenComparing(DetalleCampo::mensaje))
                .toList();

        ErrorResponse cuerpo = cuerpo(CodigoError.VALIDACION_FALLIDA,
                "La petición contiene campos inválidos.", detalles, rutaDe(request));
        return new ResponseEntity<>(cuerpo, HttpStatus.BAD_REQUEST);
    }

    /** Punto unico por el que pasan el resto de excepciones de Spring MVC. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        if (status.is5xxServerError()) {
            log.error("Error interno resuelto por Spring MVC en {}", rutaDe(request), ex);
        }
        ErrorResponse cuerpo = cuerpo(codigoPara(status), mensajeSeguro(ex, status), null,
                rutaDe(request));
        return new ResponseEntity<>(cuerpo, headers, status);
    }

    // -------------------------------------------------------------- utilidades

    /**
     * Traduce cada excepcion de Spring MVC a un mensaje redactado por nosotros.
     *
     * <p>El orden de los patrones importa: {@link MethodArgumentTypeMismatchException} es
     * subclase de {@link TypeMismatchException} y debe aparecer antes, o el compilador
     * rechaza la rama por quedar dominada.</p>
     */
    private String mensajeSeguro(Exception ex, HttpStatusCode status) {
        return switch (ex) {
            case HttpMessageNotReadableException ignorada ->
                    "El cuerpo de la petición no es un JSON válido o algún campo tiene un formato incorrecto.";
            case MissingServletRequestParameterException falta ->
                    "Falta el parámetro obligatorio '%s'.".formatted(falta.getParameterName());
            case MethodArgumentTypeMismatchException desajuste -> mensajeDeParametro(desajuste);
            case TypeMismatchException ignorada ->
                    "Algún parámetro de la petición no tiene el formato esperado.";
            case HttpRequestMethodNotSupportedException metodo ->
                    "El método HTTP %s no está permitido sobre este recurso.".formatted(metodo.getMethod());
            case HttpMediaTypeNotSupportedException ignorada ->
                    "El tipo de contenido enviado no está soportado. Use application/json.";
            case NoResourceFoundException ignorada -> "El recurso solicitado no existe.";
            default -> status.is5xxServerError() ? MENSAJE_ERROR_INTERNO : "La petición no es válida.";
        };
    }

    /**
     * Mensaje para un parametro que no se pudo convertir al tipo esperado.
     *
     * <p>Cuando ese tipo es un enum se enumeran los valores admitidos. Es informacion
     * nuestra, no del usuario, y ahorra al cliente tener que abrir la documentacion para
     * descubrir que {@code estado=cancelada} debia escribirse {@code CANCELADA}.</p>
     */
    private String mensajeDeParametro(MethodArgumentTypeMismatchException ex) {
        Class<?> tipoEsperado = ex.getRequiredType();
        if (tipoEsperado != null && tipoEsperado.isEnum()) {
            String admitidos = Arrays.stream(tipoEsperado.getEnumConstants())
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
            return "El parámetro '%s' solo admite los valores: %s."
                    .formatted(ex.getName(), admitidos);
        }
        return "El parámetro '%s' no tiene un formato válido.".formatted(ex.getName());
    }

    private CodigoError codigoPara(HttpStatusCode status) {
        if (status.is5xxServerError()) {
            return CodigoError.ERROR_INTERNO;
        }
        return status.value() == HttpStatus.NOT_FOUND.value()
                ? CodigoError.RECURSO_NO_ENCONTRADO
                : CodigoError.PETICION_INVALIDA;
    }

    private ResponseEntity<ErrorResponse> respuesta(
            HttpStatus estado, CodigoError codigo, String mensaje,
            List<DetalleCampo> detalles, String ruta) {
        return ResponseEntity.status(estado).body(cuerpo(codigo, mensaje, detalles, ruta));
    }

    private ErrorResponse cuerpo(
            CodigoError codigo, String mensaje, List<DetalleCampo> detalles, String ruta) {
        return new ErrorResponse(codigo, mensaje, OffsetDateTime.now(clock), ruta, detalles);
    }

    private String rutaDe(WebRequest request) {
        return request instanceof ServletWebRequest servlet
                ? servlet.getRequest().getRequestURI()
                : request.getDescription(false);
    }
}
