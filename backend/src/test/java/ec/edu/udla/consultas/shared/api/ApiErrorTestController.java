package ec.edu.udla.consultas.shared.api;

import ec.edu.udla.consultas.contribuyentes.adapter.in.rest.dto.ConsultarContribuyenteRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/__test/api")
class ApiErrorTestController {

    @PostMapping("/contribuyentes")
    void validateContributorRequest(@Valid @RequestBody ConsultarContribuyenteRequest request) {
    }

    @GetMapping("/failure")
    void failUnexpectedly() {
        throw new IllegalStateException("PRIVATE-DETAIL-MUST-NOT-LEAK");
    }
}
