package ec.edu.udla.consultas.shared.integration.sri;

import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class E2eSriEndpointGuardTests {

    @Test
    void acceptsEndpointsOnOneLocalHttpStub() {
        assertDoesNotThrow(() -> new E2eSriEndpointGuard(
                URI.create("http://127.0.0.1:41001/sri/existence"),
                URI.create("http://127.0.0.1:41001/sri/details"),
                URI.create("http://127.0.0.1:41001/sri/vehicle")));
    }

    @Test
    void rejectsAnExternalOrMismatchedSriEndpointInE2eProfile() {
        assertThrows(IllegalStateException.class, () -> new E2eSriEndpointGuard(
                URI.create("http://127.0.0.1:41001/sri/existence"),
                URI.create("https://srienlinea.sri.gob.ec/sri/details"),
                URI.create("http://127.0.0.1:41001/sri/vehicle")));
    }
}
