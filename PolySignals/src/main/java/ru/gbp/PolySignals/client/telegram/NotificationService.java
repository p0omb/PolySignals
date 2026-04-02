package ru.gbp.PolySignals.client.telegram;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import java.util.Objects;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final String DEFAULT_TELEGRAM_API_BASE_URL = "https://api.telegram.org";

    private final ObjectProvider<WebClient.Builder> webClientBuilderProvider;
    private final TelegramMessageFormatter telegramMessageFormatter;

    @Value("${telegram.bot.token}")
    private String botToken;

    @Value("${telegram.bot.chat-id}")
    private String chatId;

    @Value("${telegram.bot.api-base-url:https://api.telegram.org}")
    private String telegramApiBaseUrl;

    private WebClient telegramWebClient;

    @PostConstruct
    void init() {
        validateConfiguration();

        WebClient.Builder builder = webClientBuilderProvider.getIfAvailable(WebClient::builder);
        this.telegramWebClient = builder
                .baseUrl(telegramApiBaseUrl)
                .build();
    }

    public void sendSignalNotification(SignalNotificationPayload payload) {
        Objects.requireNonNull(payload, "payload must not be null");

        String text = telegramMessageFormatter.formatSignal(payload);
        if (!hasText(text)) {
            throw new IllegalArgumentException("formatted telegram message must not be blank");
        }

        telegramWebClient.post()
                .uri("/bot{token}/sendMessage", botToken)
                .bodyValue(new TelegramSendMessageRequest(
                        chatId,
                        text,
                        "HTML",
                        true
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response ->
                        response.bodyToMono(String.class)
                                .defaultIfEmpty("")
                                .map(body -> new TelegramApiException(
                                        response.statusCode().value(),
                                        body
                                )))
                .toBodilessEntity()
                .onErrorMap(WebClientRequestException.class,
                        ex -> new TelegramTransportException("Telegram transport error: " + ex.getMessage(), ex))
                .block();
    }

    private void validateConfiguration() {
        if (!hasText(botToken)) {
            throw new IllegalStateException("telegram.bot.token must not be blank");
        }
        if (!hasText(chatId)) {
            throw new IllegalStateException("telegram.bot.chat-id must not be blank");
        }
        if (!hasText(telegramApiBaseUrl)) {
            telegramApiBaseUrl = DEFAULT_TELEGRAM_API_BASE_URL;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private record TelegramSendMessageRequest(
            String chat_id,
            String text,
            String parse_mode,
            boolean disable_web_page_preview
    ) {
    }

    public static final class TelegramApiException extends RuntimeException {
        private final int statusCode;
        private final String responseBody;

        public TelegramApiException(int statusCode, String responseBody) {
            super("Telegram API error | status=" + statusCode + " | body=" + sanitize(responseBody));
            this.statusCode = statusCode;
            this.responseBody = responseBody;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        private static String sanitize(String body) {
            if (body == null) {
                return "";
            }
            String normalized = body.replace('\n', ' ').replace('\r', ' ').trim();
            return normalized.length() > 2000 ? normalized.substring(0, 2000) + "...(truncated)" : normalized;
        }
    }

    public static final class TelegramTransportException extends RuntimeException {
        public TelegramTransportException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
