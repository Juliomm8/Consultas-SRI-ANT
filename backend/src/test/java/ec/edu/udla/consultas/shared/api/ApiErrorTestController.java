package ec.edu.udla.consultas.shared.api;

import ec.edu.udla.consultas.contribuyentes.adapter.in.rest.dto.ConsultarContribuyenteRequest;
import jakarta.servlet.ServletException;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/__test/api")
class ApiErrorTestController {

    static final String SYNTHETIC_RUC = "9912345678901";
    static final String SYNTHETIC_EMAIL = "log-privacy@example.invalid";
    static final String SYNTHETIC_PLATE = "ZZZ-9876";
    static final String SYNTHETIC_TOKEN = "synthetic-token-do-not-log";
    static final String SENSITIVE_URL = "https://example.invalid/lookup?numeroRuc=" + SYNTHETIC_RUC
            + "&email=" + SYNTHETIC_EMAIL + "&placa=" + SYNTHETIC_PLATE + "&token=" + SYNTHETIC_TOKEN;

    @PostMapping("/contribuyentes")
    void validateContributorRequest(@Valid @RequestBody ConsultarContribuyenteRequest request) {
    }

    @GetMapping("/failure")
    void failUnexpectedly() throws ServletException {
        throw new ServletException(
                "synthetic servlet wrapper " + SENSITIVE_URL,
                new IllegalStateException(
                        "synthetic application failure " + SYNTHETIC_EMAIL,
                        new IllegalArgumentException("synthetic root cause " + SYNTHETIC_PLATE)));
    }
}
