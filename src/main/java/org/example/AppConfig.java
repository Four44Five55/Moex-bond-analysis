package org.example;

import java.io.InputStream;
import java.util.Properties;

public class AppConfig {
    private final Properties properties;

    public AppConfig() throws Exception {
        properties = new Properties();
        try (InputStream input = getClass().getResourceAsStream("/config.properties")) {
            properties.load(input);
        }
    }

    public String getApiUrl() {
        return properties.getProperty("moex.api.url");
    }

    public double getMinYield() {
        return Double.parseDouble(properties.getProperty("min.yield", "5.0"));
    }

    public String getOutputPath() {
        return properties.getProperty("output.path", "output/bonds.xlsx");
    }
}