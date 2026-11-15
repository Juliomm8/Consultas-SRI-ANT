package ec.edu.udla.consultas.vehiculos.adapter.in.rest;

import ec.edu.udla.consultas.shared.api.ApiMeta;
import ec.edu.udla.consultas.shared.api.ApiResponse;
import ec.edu.udla.consultas.shared.api.RequestIdFilter;
import ec.edu.udla.consultas.vehiculos.adapter.in.rest.dto.ConsultarVehiculoRequest;
import ec.edu.udla.consultas.vehiculos.adapter.in.rest.dto.VehiculoResponseDto;
import ec.edu.udla.consultas.vehiculos.application.port.in.ConsultarVehiculoQuery;
import ec.edu.udla.consultas.vehiculos.application.port.in.ConsultarVehiculoUseCase;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/vehiculos")
public class VehiculoRestController {

    private final ConsultarVehiculoUseCase consultarVehiculoUseCase;

    public VehiculoRestController(ConsultarVehiculoUseCase consultarVehiculoUseCase) {
        this.consultarVehiculoUseCase = consultarVehiculoUseCase;
    }

    @PostMapping("/consultas")
    public ResponseEntity<ApiResponse<List<VehiculoResponseDto>>> consultar(
            @Valid @RequestBody ConsultarVehiculoRequest request,
            HttpServletRequest httpRequest) {
        var query = new ConsultarVehiculoQuery(request.placa());
        List<VehiculoResponseDto> data = consultarVehiculoUseCase.consultar(query).stream()
                .map(VehiculoResponseDto::from)
                .toList();
        ApiMeta meta = createMeta(httpRequest);
        return ResponseEntity.ok()
                .header(RequestIdFilter.HEADER_NAME, meta.requestId())
                .body(new ApiResponse<>(data, meta));
    }

    private static ApiMeta createMeta(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId instanceof String value && !value.isBlank()) {
            return new ApiMeta(value, "SRI", Instant.now());
        }
        return new ApiMeta(UUID.randomUUID().toString(), "SRI", Instant.now());
    }
}
