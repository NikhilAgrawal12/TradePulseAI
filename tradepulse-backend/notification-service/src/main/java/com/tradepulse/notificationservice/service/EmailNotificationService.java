package com.tradepulse.notificationservice.service;

import com.tradepulse.notificationservice.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${tradepulse.mail.from:no-reply@tradepulse.local}") String fromAddress
    ) {
        this.mailSender = mailSenderProvider.getIfAvailable();
        this.fromAddress = fromAddress;
    }

    public void sendNotification(NotificationEvent event, String toEmail) {
        if (mailSender == null) {
            log.error("Mail sender is not configured. Cannot send notification for eventType={}, userId={}",
                    event.getEventType(), event.getUserId());
            return;
        }

        if (toEmail == null || toEmail.isBlank()) {
            log.warn("No email address available for userId={}, skipping notification for eventType={}",
                    event.getUserId(), event.getEventType());
            return;
        }

        String subject = buildSubject(event);
        String body = buildBody(event);

        if (subject == null) {
            log.warn("Unknown eventType={}, skipping notification for userId={}", event.getEventType(), event.getUserId());
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Notification email sent: eventType={}, userId={}, to={}", event.getEventType(), event.getUserId(), toEmail);
        } catch (Exception ex) {
            log.error("Failed to send notification email for eventType={}, userId={}: {}",
                    event.getEventType(), event.getUserId(), ex.getMessage(), ex);
        }
    }

    private String buildSubject(NotificationEvent event) {
        return switch (event.getEventType()) {
            case "ACCOUNT_CREATED"   -> "Welcome to TradePulse – Account Created";
            case "WALLET_DEPOSIT"    -> "TradePulse – Wallet Deposit Successful";
            case "WALLET_WITHDRAWAL" -> "TradePulse – Wallet Withdrawal Successful";
            case "STOCK_PURCHASED"   -> "TradePulse – Order Completed";
            case "STOCK_SOLD"        -> "TradePulse – Sell Order Settled";
            default -> null;
        };
    }

    private String buildBody(NotificationEvent event) {
        Map<String, Object> data = event.getData();

        return switch (event.getEventType()) {
            case "ACCOUNT_CREATED" -> {
                String firstName = getString(data, "firstName", "");
                String lastName = getString(data, "lastName", "");
                String fullName = (firstName + " " + lastName).trim();
                if (fullName.isEmpty()) {
                    fullName = "Valued Customer";
                }
                yield """
                        Hello %s,

                        Welcome to TradePulse! Your account has been successfully created.
                        You can now log in, add funds to your wallet, and start trading.

                        Happy trading!
                        — The TradePulse Team
                        """.formatted(fullName);
            }
            case "WALLET_DEPOSIT" -> {
                String firstName = getString(data, "firstName", "");
                String lastName = getString(data, "lastName", "");
                String fullName = (firstName + " " + lastName).trim();
                if (fullName.isEmpty()) {
                    fullName = "Valued Customer";
                }
                String transactionId = getString(data, "transactionId", "N/A");
                String amount  = getString(data, "amount", "0.00");
                String balance = getString(data, "newBalance", "0.00");
                yield """
                        Hi %s,

                        Your deposit of $%s has been successfully processed.
                        Transaction ID : %s
                        New Balance    : $%s

                        — The TradePulse Team
                        """.formatted(fullName, amount, transactionId, balance);
            }
            case "WALLET_WITHDRAWAL" -> {
                String firstName = getString(data, "firstName", "");
                String lastName = getString(data, "lastName", "");
                String fullName = (firstName + " " + lastName).trim();
                if (fullName.isEmpty()) {
                    fullName = "Valued Customer";
                }
                String transactionId = getString(data, "transactionId", "N/A");
                String amount  = getString(data, "amount", "0.00");
                String balance = getString(data, "newBalance", "0.00");
                yield """
                        Hi %s,

                        Your withdrawal of $%s has been successfully processed.
                        Transaction ID : %s
                        New Balance    : $%s

                        — The TradePulse Team
                        """.formatted(fullName, amount, transactionId, balance);
            }
            case "STOCK_PURCHASED" -> {
                String firstName = getString(data, "firstName", "");
                String lastName = getString(data, "lastName", "");
                String fullName = (firstName + " " + lastName).trim();
                if (fullName.isEmpty()) {
                    fullName = "Valued Customer";
                }
                String orderId = getString(data, "orderId", "N/A");
                String total    = getString(data, "total", "0.00");
                String itemsBlock = buildPurchasedItemsBlock(data);
                yield """
                        Hi %s,

                        Your stock purchase order has been completed successfully.
                        Order ID  : %s
                        %s
                        Total     : $%s

                        Your portfolio has been updated.

                        — The TradePulse Team
                        """.formatted(fullName, orderId, itemsBlock, total);
            }
            case "STOCK_SOLD" -> {
                String firstName = getString(data, "firstName", "");
                String lastName = getString(data, "lastName", "");
                String fullName = (firstName + " " + lastName).trim();
                if (fullName.isEmpty()) {
                    fullName = "Valued Customer";
                }
                String symbol   = getString(data, "symbol", "N/A");
                String quantity = getString(data, "quantity", "0");
                String price    = getString(data, "price", "0.00");
                String total    = getString(data, "total", "0.00");
                yield """
                        Hi %s,

                        Your sell order has been settled successfully.
                        Stock    : %s
                        Quantity : %s %s
                        Price    : $%s per share
                        Total    : $%s credited to your wallet

                        — The TradePulse Team
                        """.formatted(fullName, symbol, quantity, shareUnit(quantity), price, total);
            }
            default -> "A new activity has been recorded on your TradePulse account.";
        };
    }

    private String getString(Map<String, Object> data, String key, String defaultValue) {
        if (data == null) return defaultValue;
        Object value = data.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private String shareUnit(String quantity) {
        if (quantity == null || quantity.isBlank()) {
            return "shares";
        }

        try {
            return new java.math.BigDecimal(quantity).compareTo(java.math.BigDecimal.ONE) == 0 ? "share" : "shares";
        } catch (NumberFormatException exception) {
            return "shares";
        }
    }

    @SuppressWarnings("unchecked")
    private String buildPurchasedItemsBlock(Map<String, Object> data) {
        Object rawItems = data != null ? data.get("items") : null;
        if (rawItems instanceof List<?> list && !list.isEmpty()) {
            List<String> lines = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                Object raw = list.get(i);
                if (!(raw instanceof Map<?, ?> itemMapRaw)) {
                    continue;
                }
                Map<String, Object> item = (Map<String, Object>) itemMapRaw;
                String symbol = getString(item, "symbol", getString(item, "stockId", "N/A"));
                String quantity = getString(item, "quantity", "0");
                String price = getString(item, "price", "0.00");
                String lineTotal = getString(item, "lineTotal", formatLineTotal(price, quantity));
                lines.add(String.format("Stock %d  : %s\nQuantity %d: %s %s\nPrice %d   : $%s per share\nLine %d    : $%s",
                        i + 1, symbol,
                        i + 1, quantity, shareUnit(quantity),
                        i + 1, price,
                        i + 1, lineTotal));
            }
            if (!lines.isEmpty()) {
                return String.join("\n\n", lines);
            }
        }

        // Backward-compatible fallback when old payload shape is received.
        String symbol = getString(data, "symbol", getString(data, "stockId", "N/A"));
        String quantity = getString(data, "quantity", "0");
        String price = getString(data, "price", "0.00");
        return """
                Stock     : %s
                Quantity  : %s %s
                Price     : $%s per share
                """.formatted(symbol, quantity, shareUnit(quantity), price).stripTrailing();
    }

    private String formatLineTotal(String price, String quantity) {
        try {
            BigDecimal p = new BigDecimal(price);
            BigDecimal q = new BigDecimal(quantity);
            return p.multiply(q).setScale(2, RoundingMode.HALF_UP).toPlainString();
        } catch (Exception exception) {
            return "0.00";
        }
    }
}

