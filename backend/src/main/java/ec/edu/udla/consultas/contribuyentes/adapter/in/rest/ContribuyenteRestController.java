package ec.edu.udla.consultas.contribuyentes.adapter.in.rest;

import ec.edu.udla.consultas.contribuyentes.adapter.in.rest.dto.ConsultarContribuyenteRequest;
import ec.edu.udla.consultas.contribuyentes.adapter.in.rest.dto.ContribuyenteResponseDto;
import ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteQuery;
import ec.edu.udla.consultas.contribuyentes.application.port.in.ConsultarContribuyenteUseCase;
import ec.edu.udla.consultas.contribuyentes.domain.Ruc;
import ec.edu.udla.consultas.shared.api.ApiMeta;
import ec.edu.udla.consultas.shared.api.ApiResponse;
import ec.edu.udla.consultas.shared.api.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/contribuyentes")
public class ContribuyenteRestController {

    private final ConsultarContribuyenteUseCase consultarContribuyenteUseCase;

    public ContribuyenteRestController(ConsultarContribuyenteUseCase consultarContribuyenteUseCase) {
        this.consultarContribuyenteUseCase = consultarContribuyenteUseCase;
    }

    @PostMapping("/consultas")
    public ResponseEntity<ApiResponse<ContribuyenteResponseDto>> consultar(
            @Valid @RequestBody ConsultarContribuyenteRequest request,
            HttpServletRequest httpRequest) {
        var query = new ConsultarContribuyenteQuery(new Ruc(request.ruc()));
        ContribuyenteResponseDto data = ContribuyenteResponseDto.from(
                consultarContribuyenteUseCase.consultar(query));
        ApiMeta meta = createMeta(httpRequest);
        return ResponseEntity.ok(new ApiResponse<>(data, meta));
    }

    private static ApiMeta createMeta(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String value && !value.isBlank()) {
            return new ApiMeta(value, "SRI", Instant.now());
        }
        throw new IllegalStateException("No se generó el identificador de solicitud.");
    }
}
