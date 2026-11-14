package ec.edu.udla.consultas.contribuyentes.application.port.in;

import ec.edu.udla.consultas.contribuyentes.domain.Ruc;

public record ConsultarContribuyenteQuery(Ruc ruc) {

    public ConsultarContribuyenteQuery {
        if (ruc == null) {
            throw new IllegalArgumentException("El RUC es obligatorio para consultar un contribuyente.");
        }
    }
}
