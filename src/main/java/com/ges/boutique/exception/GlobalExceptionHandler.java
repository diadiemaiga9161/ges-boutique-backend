package com.ges.boutique.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.OptimisticLockException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final String TIMESTAMP = "timestamp";
    private static final String MESSAGE = "message";
    private static final String STATUS = "status";
    private static final String ERROR = "error";
    private static final String PATH = "path";
    private static final String ERRORS = "errors";
    private static final String ERROR_CODE = "errorCode";

    /**
     * Handle custom resource not found exception
     */
    @ExceptionHandler(RessourceIntrouvableException.class)
    public ResponseEntity<Map<String, Object>> handleRessourceIntrouvable(
            RessourceIntrouvableException ex,
            WebRequest request) {

        log.warn("Resource not found: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                ex.getMessage(),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "RESOURCE_NOT_FOUND");

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle insufficient stock exception
     */
    @ExceptionHandler(StockInsuffisantException.class)
    public ResponseEntity<Map<String, Object>> handleStockInsuffisant(
            StockInsuffisantException ex,
            WebRequest request) {

        log.warn("Insufficient stock: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Insufficient Stock",
                ex.getMessage(),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "INSUFFICIENT_STOCK");
        if (ex.getProduitId() != null) {
            body.put("produitId", ex.getProduitId());
        }
        if (ex.getStockDisponible() != null) {
            body.put("stockDisponible", ex.getStockDisponible());
        }
        if (ex.getStockRequis() != null) {
            body.put("stockRequis", ex.getStockRequis());
        }

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle custom validation exception
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            ValidationException ex,
            WebRequest request) {

        log.warn("Validation error: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                ex.getMessage(),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "VALIDATION_ERROR");
        if (ex.getField() != null) {
            body.put("field", ex.getField());
        }

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle Spring Validation (MethodArgumentNotValidException)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            WebRequest request) {

        List<Map<String, String>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::convertToErrorMap)
                .collect(Collectors.toList());

        log.warn("Method argument validation failed: {}", errors);

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Validation Failed",
                "Les données fournies sont invalides",
                request.getDescription(false)
        );
        body.put(ERRORS, errors);
        body.put(ERROR_CODE, "INVALID_REQUEST_BODY");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle Constraint Violation Exception
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex,
            WebRequest request) {

        List<Map<String, String>> errors = ex.getConstraintViolations()
                .stream()
                .map(this::convertConstraintViolationToErrorMap)
                .collect(Collectors.toList());

        log.warn("Constraint violation: {}", errors);

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Constraint Violation",
                "Violation des contraintes de validation",
                request.getDescription(false)
        );
        body.put(ERRORS, errors);
        body.put(ERROR_CODE, "CONSTRAINT_VIOLATION");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle illegal argument exceptions (validation métier)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(
            IllegalArgumentException ex,
            WebRequest request) {

        log.warn("Illegal argument: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage(),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "INVALID_ARGUMENT");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle illegal state exceptions (caisse fermée, employé inactif, etc.)
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(
            IllegalStateException ex,
            WebRequest request) {

        log.warn("Illegal state: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                ex.getMessage(),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "ILLEGAL_STATE");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle insufficient balance exception
     */
    @ExceptionHandler(SoldeInsuffisantException.class)
    public ResponseEntity<Map<String, Object>> handleSoldeInsuffisant(
            SoldeInsuffisantException ex,
            WebRequest request) {

        log.warn("Solde insuffisant: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Solde Insuffisant",
                ex.getMessage(),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "SOLDE_INSUFFISANT");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle optimistic locking failures (concurrent stock modifications)
     */
    @ExceptionHandler({OptimisticLockException.class, ObjectOptimisticLockingFailureException.class})
    public ResponseEntity<Map<String, Object>> handleOptimisticLock(
            Exception ex,
            WebRequest request) {

        log.warn("Optimistic lock conflict: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.CONFLICT,
                "Concurrent Modification",
                "Le stock a été modifié simultanément — veuillez réessayer",
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "OPTIMISTIC_LOCK_CONFLICT");

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    /**
     * Handle bad credentials exception
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleBadCredentials(
            BadCredentialsException ex,
            WebRequest request) {

        log.warn("Authentication failed: Bad credentials");

        Map<String, Object> body = createErrorBody(
                HttpStatus.UNAUTHORIZED,
                "Authentication Failed",
                "Nom d'utilisateur ou mot de passe incorrect",
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "BAD_CREDENTIALS");

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle other authentication exceptions
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthentication(
            AuthenticationException ex,
            WebRequest request) {

        log.warn("Authentication failed: {}", ex.getMessage());

        String message = "Échec d'authentification";
        String errorCode = "AUTHENTICATION_FAILED";

        if (ex instanceof DisabledException) {
            message = "Le compte utilisateur est désactivé";
            errorCode = "ACCOUNT_DISABLED";
        } else if (ex instanceof LockedException) {
            message = "Le compte utilisateur est verrouillé";
            errorCode = "ACCOUNT_LOCKED";
        }

        Map<String, Object> body = createErrorBody(
                HttpStatus.UNAUTHORIZED,
                "Authentication Failed",
                message,
                request.getDescription(false)
        );
        body.put(ERROR_CODE, errorCode);

        return new ResponseEntity<>(body, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Handle access denied exception
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(
            AccessDeniedException ex,
            WebRequest request) {

        log.warn("Access denied: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.FORBIDDEN,
                "Access Denied",
                "Vous n'avez pas les permissions nécessaires pour accéder à cette ressource",
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "ACCESS_DENIED");

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    /**
     * Handle rôle personnalisé sans le droit demandé (role.PermissionInterceptor)
     */
    @ExceptionHandler(PermissionRefuseeException.class)
    public ResponseEntity<Map<String, Object>> handlePermissionRefusee(
            PermissionRefuseeException ex,
            WebRequest request) {

        log.warn("Permission refusée: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.FORBIDDEN,
                "Permission refusée",
                ex.getMessage(),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "ROLE_PERMISSION_DENIED");

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    /**
     * Handle fonctionnalité désactivée par le super admin (@RequireFeature)
     */
    @ExceptionHandler(FonctionnaliteDesactiveeException.class)
    public ResponseEntity<Map<String, Object>> handleFonctionnaliteDesactivee(
            FonctionnaliteDesactiveeException ex,
            WebRequest request) {

        log.warn("Fonctionnalité désactivée: {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.FORBIDDEN,
                "Fonctionnalité désactivée",
                ex.getMessage(),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "FEATURE_DISABLED");

        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    /**
     * Handle JWT exceptions
     */
    @ExceptionHandler(JwtException.class)
    public ResponseEntity<Map<String, Object>> handleJwtException(
            JwtException ex,
            WebRequest request) {

        log.warn("JWT error: {}", ex.getMessage());

        String message = "Token JWT invalide";
        HttpStatus status = HttpStatus.UNAUTHORIZED;
        String errorCode = "INVALID_TOKEN";

        if (ex instanceof ExpiredJwtException) {
            message = "Token JWT expiré";
            errorCode = "TOKEN_EXPIRED";
        }

        Map<String, Object> body = createErrorBody(
                status,
                "Authentication Error",
                message,
                request.getDescription(false)
        );
        body.put(ERROR_CODE, errorCode);

        return new ResponseEntity<>(body, status);
    }

    /**
     * Handle data integrity violation (duplicate entries, foreign key violations)
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex,
            WebRequest request) {

        log.error("Data integrity violation: {}", ex.getMessage(), ex);

        String message = "Violation d'intégrité des données";
        String rootMessage = ex.getRootCause() != null ? ex.getRootCause().getMessage() : ex.getMessage();

        if (rootMessage != null) {
            if (rootMessage.contains("Duplicate entry")) {
                message = "Cette donnée existe déjà";
            } else if (rootMessage.contains("foreign key constraint")) {
                message = "Impossible de supprimer cette ressource car elle est référencée ailleurs";
            }
        }

        Map<String, Object> body = createErrorBody(
                HttpStatus.CONFLICT,
                "Data Integrity Violation",
                message,
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "DATA_INTEGRITY_VIOLATION");

        return new ResponseEntity<>(body, HttpStatus.CONFLICT);
    }

    /**
     * Handle database access exceptions
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccessException(
            DataAccessException ex,
            WebRequest request) {

        log.error("Database access error: {}", ex.getMessage(), ex);

        Map<String, Object> body = createErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Database Error",
                "Une erreur de base de données est survenue",
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "DATABASE_ERROR");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Handle invalid JSON format
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            WebRequest request) {

        log.warn("Invalid JSON format: {}", ex.getMessage());

        String message = "Format JSON invalide";

        if (ex.getCause() instanceof InvalidFormatException) {
            InvalidFormatException ife = (InvalidFormatException) ex.getCause();
            message = String.format("Valeur invalide '%s' pour le champ '%s'. Attendu: %s",
                    ife.getValue(),
                    ife.getPath().get(ife.getPath().size() - 1).getFieldName(),
                    ife.getTargetType().getSimpleName());
        }

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Invalid JSON",
                message,
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "INVALID_JSON_FORMAT");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle missing request parameters
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParams(
            MissingServletRequestParameterException ex,
            WebRequest request) {

        log.warn("Missing request parameter: {}", ex.getParameterName());

        String message = String.format("Le paramètre '%s' est requis", ex.getParameterName());

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Missing Parameter",
                message,
                request.getDescription(false)
        );
        body.put("parameter", ex.getParameterName());
        body.put(ERROR_CODE, "MISSING_PARAMETER");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle method argument type mismatch
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex,
            WebRequest request) {

        log.warn("Type mismatch: {} for parameter {}", ex.getValue(), ex.getName());

        String message = String.format("Type invalide pour le paramètre '%s'. Attendu: %s",
                ex.getName(),
                Objects.requireNonNull(ex.getRequiredType()).getSimpleName());

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Type Mismatch",
                message,
                request.getDescription(false)
        );
        body.put("parameter", ex.getName());
        body.put("expectedType", Objects.requireNonNull(ex.getRequiredType()).getSimpleName());
        body.put(ERROR_CODE, "TYPE_MISMATCH");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle missing static resources (Angular chunks, CSS, etc.) - retourne 404 pas 500
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(
            NoResourceFoundException ex,
            WebRequest request) {

        Map<String, Object> body = createErrorBody(
                HttpStatus.NOT_FOUND,
                "Not Found",
                "Ressource statique introuvable : " + ex.getResourcePath(),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "STATIC_RESOURCE_NOT_FOUND");

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    /**
     * Handle 404 errors
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoHandlerFound(
            NoHandlerFoundException ex,
            WebRequest request) {

        log.warn("No handler found: {} {}", ex.getHttpMethod(), ex.getRequestURL());

        Map<String, Object> body = createErrorBody(
                HttpStatus.NOT_FOUND,
                "Endpoint Not Found",
                String.format("L'endpoint %s %s n'existe pas", ex.getHttpMethod(), ex.getRequestURL()),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "ENDPOINT_NOT_FOUND");
        body.put("method", ex.getHttpMethod());
        body.put("url", ex.getRequestURL());

        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }

    /**
     * Gère les RuntimeException levées telles quelles (throw new RuntimeException("message"))
     * — convention déjà utilisée dans plusieurs services (CaisseServiceImpl, TypeDepenseServiceImpl,
     * CategorieServiceImpl, ProduitServiceImpl, UtilisateurServiceImpl, ClientReleveService) pour
     * des erreurs métier avec un message déjà rédigé en français ("Le nom d'utilisateur existe déjà",
     * "Un type de dépense avec ce nom existe déjà"...). Sans ce handler, ces exceptions tombaient
     * dans handleGlobalException ci-dessous, qui jette le vrai message et renvoie 500 + un texte
     * générique — le client ne voyait jamais la vraie raison (ex: doublon de nom d'utilisateur).
     *
     * Ne s'applique qu'à la classe RuntimeException exacte, jamais à ses sous-classes (une
     * NullPointerException, ClassCastException... reste un vrai bug technique et doit continuer
     * à retomber dans le catch-all générique — voir ex.getClass() != RuntimeException.class ci-dessous).
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleRuntimeException(
            RuntimeException ex,
            WebRequest request) {

        if (ex.getClass() != RuntimeException.class) {
            return handleGlobalException(ex, request);
        }

        log.warn("Erreur métier (RuntimeException) : {}", ex.getMessage());

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                MessageErreurUtil.messageClient(ex),
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "BUSINESS_ERROR");

        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /**
     * Handle all other exceptions
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGlobalException(
            Exception ex,
            WebRequest request) {

        log.error("Unexpected error: {}", ex.getMessage(), ex);

        Map<String, Object> body = createErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "Une erreur interne est survenue. Veuillez réessayer plus tard.",
                request.getDescription(false)
        );
        body.put(ERROR_CODE, "INTERNAL_SERVER_ERROR");

        return new ResponseEntity<>(body, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Helper method to create standardized error response body
     */
    private Map<String, Object> createErrorBody(
            HttpStatus status,
            String error,
            String message,
            String path) {

        Map<String, Object> body = new HashMap<>();
        body.put(TIMESTAMP, LocalDateTime.now());
        body.put(STATUS, status.value());
        body.put(ERROR, error);
        body.put(MESSAGE, message);
        body.put(PATH, path.replace("uri=", ""));

        return body;
    }

    /**
     * Convert FieldError to error map
     */
    private Map<String, String> convertToErrorMap(FieldError fieldError) {
        Map<String, String> error = new HashMap<>();
        error.put("field", fieldError.getField());
        error.put("message", fieldError.getDefaultMessage());
        if (fieldError.getRejectedValue() != null) {
            error.put("rejectedValue", fieldError.getRejectedValue().toString());
        }
        return error;
    }

    /**
     * Convert ConstraintViolation to error map
     */
    private Map<String, String> convertConstraintViolationToErrorMap(ConstraintViolation<?> violation) {
        Map<String, String> error = new HashMap<>();
        error.put("field", violation.getPropertyPath().toString());
        error.put("message", violation.getMessage());
        error.put("invalidValue", violation.getInvalidValue() != null ?
                violation.getInvalidValue().toString() : "null");
        return error;
    }
}