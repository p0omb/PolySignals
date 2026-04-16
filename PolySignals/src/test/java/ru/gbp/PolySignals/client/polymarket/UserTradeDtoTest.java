package ru.gbp.PolySignals.client.polymarket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.gbp.PolySignals.client.polymarket.dto.UserTradeDto;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class UserTradeDtoTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void mapsConditionIdOutcomeAndTimestamp() throws Exception {
        String json = """
                {
                  "conditionId": "m-123",
                  "outcome": "Yes",
                  "size": "12.5",
                  "timestamp": 1735689600,
                  "transactionHash": "0xabc"
                }
                """;

        UserTradeDto dto = objectMapper.readValue(json, UserTradeDto.class);

        assertEquals("m-123", dto.getMarketId());
        assertEquals("Yes", dto.getOutcome());
        assertEquals(Instant.ofEpochSecond(1735689600L), dto.getTimestamp());
        assertEquals("0xabc", dto.getTransactionHash());
    }

    @Test
    void fallsBackToAssetWhenOutcomeMissing() throws Exception {
        String json = """
                {
                  "condition_id": "m-456",
                  "asset": 1,
                  "timestamp": "1735689600000"
                }
                """;

        UserTradeDto dto = objectMapper.readValue(json, UserTradeDto.class);

        assertEquals("m-456", dto.getMarketId());
        assertEquals("1", dto.getAsset());
        assertEquals("1", dto.getOutcome());
        assertEquals(Instant.ofEpochMilli(1735689600000L), dto.getTimestamp());
    }
}

