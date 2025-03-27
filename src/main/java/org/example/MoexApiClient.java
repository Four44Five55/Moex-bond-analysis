package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.*;

public class MoexApiClient {
    private static final Logger logger = LoggerFactory.getLogger(MoexApiClient.class);
    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();

    public List<Bond> fetchBonds() throws Exception {
        List<Bond> bonds = fetchBasicBondData();

        // Загрузка рейтингов теперь опциональна
        try {
            Map<String, String> ratings = fetchIssuerRatings();
            if (!ratings.isEmpty()) {
                applyRatingsToBonds(bonds, ratings);
            } else {
                setDefaultRatings(bonds);
            }
        } catch (Exception e) {
            logger.warn("Не удалось загрузить рейтинги: {}", e.getMessage());
            setDefaultRatings(bonds);
        }

        return bonds;
    }

    private List<Bond> fetchBasicBondData() throws Exception {
        String url = "https://iss.moex.com/iss/engines/stock/markets/bonds/securities.json" +
                "?iss.meta=off&securities.columns=" +
                "SECID,SHORTNAME,FACEVALUE,PRICE,COUPONPERCENT," +
                "MATDATE,ISSUEDATE,YIELD,DURATION,COUPONFREQUENCY,CALLABLE,TRADEVOLUME";

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            checkResponse(response);
            JsonNode root = mapper.readTree(response.body().string());

            if (!root.has("securities") || !root.get("securities").has("data")) {
                throw new RuntimeException("Неверная структура JSON ответа");
            }

            return parseBonds(root.get("securities").get("data"));
        }
    }

    private Map<String, String> fetchIssuerRatings() throws Exception {
        // Альтернативный источник рейтингов (пример)
        String url = "https://iss.moex.com/iss/securities/ratings.json";

        Request request = new Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            checkResponse(response);
            JsonNode root = mapper.readTree(response.body().string());

            if (!root.has("ratings")) {
                return Collections.emptyMap();
            }

            return parseRatings(root.get("ratings"));
        }
    }

    private List<Bond> parseBonds(JsonNode data) {
        List<Bond> bonds = new ArrayList<>();
        for (JsonNode node : data) {
            try {
                bonds.add(createBondFromNode(node));
            } catch (Exception e) {
                logger.warn("Ошибка парсинга облигации: {}", e.getMessage());
            }
        }
        return bonds;
    }

    private Bond createBondFromNode(JsonNode node) {
        Bond bond = new Bond();
        bond.setSecId(getSafeText(node, 0));
        bond.setFullName(getSafeText(node, 1));
        bond.setPricePercent(getSafeDouble(node, 3));
        bond.setCouponRate(getSafeDouble(node, 4));
        bond.setYieldToMaturity(getSafeDouble(node, 7));
        bond.setDuration(getSafeDouble(node, 8));
        bond.setCouponFrequency(getSafeInt(node, 9));
        bond.setCallable(getSafeBoolean(node, 10));
        bond.setTradeVolume(getSafeDouble(node, 11));

        try {
            bond.setMaturityDate(LocalDate.parse(getSafeText(node, 5)));
            bond.setIssueDate(LocalDate.parse(getSafeText(node, 6)));
        } catch (Exception e) {
            logger.warn("Ошибка парсинга даты: {}", e.getMessage());
        }

        bond.setQualification(bond.getYieldToMaturity() > 15.0 ? "Квалифицированный" : "Неквалифицированный");
        return bond;
    }

    private Map<String, String> parseRatings(JsonNode ratingsNode) {
        Map<String, String> ratings = new HashMap<>();
        if (ratingsNode.isArray()) {
            for (JsonNode node : ratingsNode) {
                if (node.size() >= 2) {
                    ratings.put(node.get(0).asText(), node.get(1).asText());
                }
            }
        }
        return ratings;
    }

    private void applyRatingsToBonds(List<Bond> bonds, Map<String, String> ratings) {
        bonds.forEach(bond -> {
            String rating = ratings.getOrDefault(bond.getSecId(), "NR");
            bond.setIssuerRating(rating);
            bond.setReliabilityScore(calculateReliabilityScore(rating));
        });
    }

    private void setDefaultRatings(List<Bond> bonds) {
        bonds.forEach(bond -> {
            bond.setIssuerRating("NR");
            bond.setReliabilityScore(5); // Среднее значение по умолчанию
        });
    }

    private int calculateReliabilityScore(String rating) {
        if (rating == null || rating.isEmpty()) return 5;
        return switch (rating) {
            case "AAA" -> 10;
            case "AA+", "AA", "AA-" -> 9;
            case "A+", "A", "A-" -> 8;
            case "BBB+", "BBB", "BBB-" -> 7;
            case "BB+", "BB", "BB-" -> 6;
            case "B+", "B", "B-" -> 5;
            case "CCC+", "CCC", "CCC-" -> 4;
            case "CC", "C", "D" -> 3;
            default -> 5;
        };
    }

    // Вспомогательные методы
    private void checkResponse(Response response) throws Exception {
        if (!response.isSuccessful()) {
            throw new RuntimeException("HTTP error " + response.code() + " for URL: " + response.request().url());
        }
    }

    private String getSafeText(JsonNode node, int index) {
        return node.has(index) ? node.get(index).asText() : "";
    }

    private double getSafeDouble(JsonNode node, int index) {
        return node.has(index) ? node.get(index).asDouble() : 0.0;
    }

    private int getSafeInt(JsonNode node, int index) {
        return node.has(index) ? node.get(index).asInt() : 0;
    }

    private boolean getSafeBoolean(JsonNode node, int index) {
        return node.has(index) && node.get(index).asBoolean(false);
    }
}