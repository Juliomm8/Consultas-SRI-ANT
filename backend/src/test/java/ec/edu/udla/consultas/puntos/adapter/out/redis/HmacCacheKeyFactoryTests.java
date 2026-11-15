package ec.edu.udla.consultas.puntos.adapter.out.redis;

import ec.edu.udla.consultas.puntos.domain.Cedula;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HmacCacheKeyFactoryTests {

    private final HmacCacheKeyFactory keyFactory = new HmacCacheKeyFactory(
            "synthetic-test-hmac-secret-value-00000000000000000000000000000000");

    @Test
    void createsDifferentOpaqueKeysForDifferentCedulas() {
        Cedula firstCedula = new Cedula("0000000000");
        Cedula secondCedula = new Cedula("1111111111");

        String firstKey = keyFactory.keyFor(firstCedula);
        String secondKey = keyFactory.keyFor(secondCedula);

        assertThat(firstKey).isNotEqualTo(secondKey);
        assertThat(firstKey).startsWith("consultas-sri-ant:ant-points:v2:")
                .matches("consultas-sri-ant:ant-points:v2:[a-f0-9]{64}")
                .doesNotContain(firstCedula.value(), secondCedula.value());
        assertThat(secondKey).matches("consultas-sri-ant:ant-points:v2:[a-f0-9]{64}")
                .doesNotContain(firstCedula.value(), secondCedula.value());
    }
}
