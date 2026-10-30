package ma.invento.pos.shared.api;

import java.util.Map;
import java.util.stream.Collectors;
import ma.invento.pos.shared.domain.AuthentificationRefuseeException;
import ma.invento.pos.shared.domain.ConflitException;
import ma.invento.pos.shared.domain.OperationInterditeException;
import ma.invento.pos.shared.domain.RegleMetierException;
import ma.invento.pos.shared.domain.RessourceIntrouvableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Traduit les exceptions en {@link ProblemDetail} (RFC 9457). Les erreurs inattendues sont
 * journalisées côté serveur et renvoyées sans détail interne.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthentificationRefuseeException.class)
    ProblemDetail authentificationRefusee(AuthentificationRefuseeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }

    @ExceptionHandler(OperationInterditeException.class)
    ProblemDetail operationInterdite(OperationInterditeException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(RessourceIntrouvableException.class)
    ProblemDetail introuvable(RessourceIntrouvableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(RegleMetierException.class)
    ProblemDetail regleMetier(RegleMetierException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(ConflitException.class)
    ProblemDetail conflit(ConflitException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail modificationConcurrente(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "Les données ont été modifiées entre-temps. Rechargez puis réessayez.");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrite(DataIntegrityViolationException e) {
        log.warn("Contrainte d'intégrité violée", e);
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT, "L'opération est en conflit avec des données existantes.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    ProblemDetail accesRefuse(AccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Accès refusé.");
    }

    /** Tri demandé sur une propriété inconnue ({@code ?sort=...}). */
    @ExceptionHandler(PropertyReferenceException.class)
    ProblemDetail proprieteInconnue(PropertyReferenceException e) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Critère de tri inconnu : " + e.getPropertyName());
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail inattendue(Exception e) {
        log.error("Erreur inattendue", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur interne du serveur.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Requête invalide.");
        Map<String, String> erreurs = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, GlobalExceptionHandler::message, (a, b) -> a));
        problem.setProperty("erreurs", erreurs);
        return ResponseEntity.badRequest().body(problem);
    }

    private static String message(FieldError error) {
        String message = error.getDefaultMessage();
        return message == null ? "invalide" : message;
    }
}
