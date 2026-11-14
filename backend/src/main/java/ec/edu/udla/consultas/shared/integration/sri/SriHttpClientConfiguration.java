package ec.edu.udla.consultas.shared.integration.sri;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class SriHttpClientConfiguration {

    @Bean
    RestClient sriRestClient(
            RestClient.Builder builder,
            @Value("${integrations.sri.http.connect-timeout:3s}") Duration connectTimeout,
            @Value("${integrations.sri.http.read-timeout:5s}") Duration readTimeout) {
        HttpClient jdkClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(jdkClient);
        requestFactory.setReadTimeout(readTimeout);
        return builder.requestFactory(requestFactory).build();
    }
}
