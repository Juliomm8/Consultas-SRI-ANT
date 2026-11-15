package ec.edu.udla.consultas.puntos.adapter.in.rest;

import ec.edu.udla.consultas.puntos.adapter.in.rest.dto.ConsultarPuntosRequest;
import ec.edu.udla.consultas.puntos.adapter.in.rest.dto.PuntosLicenciaResponseDto;
import ec.edu.udla.consultas.puntos.application.port.in.ConsultarPuntosLicenciaUseCase;
import ec.edu.udla.consultas.puntos.domain.Cedula;
import ec.edu.udla.consultas.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Local-only entry point for the explicitly simulated points provider. */
@RestController
@Profile("local-simulation")
@ConditionalOnProperty(prefix = "app.ant.simulation", name = "enabled", havingValue = "true")
@RequestMapping("/api/v1/licencias/puntos")
public class PuntosLicenciaRestController {

    private final ConsultarPuntosLicenciaUseCase useCase;

    public PuntosLicenciaRestController(ConsultarPuntosLicenciaUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/consultas")
    public ResponseEntity<PuntosLicenciaResponseDto> consultar(
            @Valid @RequestBody ConsultarPuntosRequest request,
            HttpServletRequest httpRequest) {
        var result = useCase.consultar(new Cedula(request.cedula()));
        Object requestId = httpRequest.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (!(requestId instanceof String value) || value.isBlank()) {
            throw new IllegalStateException("No se generó el identificador de solicitud.");
        }
        var response = new PuntosLicenciaResponseDto(
                new PuntosLicenciaResponseDto.Data(result.totalPuntos()),
                new PuntosLicenciaResponseDto.Meta(
                        value,
                        result.source().name(),
                        Instant.now(),
                        result.fetchedAt(),
                        result.stale(),
                        result.warning()));
        return ResponseEntity.ok(response);
    }
}
