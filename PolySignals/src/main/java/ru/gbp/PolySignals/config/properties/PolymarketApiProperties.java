package ru.gbp.PolySignals.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "polymarket")
public class PolymarketApiProperties {

    private Api dataApi = new Api();
    private Api gammaApi = new Api();

    public Api getDataApi() {
        return dataApi;
    }

    public void setDataApi(Api dataApi) {
        this.dataApi = dataApi;
    }

    public Api getGammaApi() {
        return gammaApi;
    }

    public void setGammaApi(Api gammaApi) {
        this.gammaApi = gammaApi;
    }

    public static class Api {
        private String baseUrl;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }
}

