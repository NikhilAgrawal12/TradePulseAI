package com.tradepulseai.notificationservice.service;

import com.tradepulseai.notificationservice.event.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class EmailNotificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public EmailNotificationService(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            @Value("${tradepulseai.mail.from:no-reply@tradepulseai.local}") String fromAddress
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
            case "ACCOUNT_CREATED"   -> "Welcome to TradePulseAI – Account Created";
            case "WALLET_DEPOSIT"    -> "TradePulseAI – Wallet Deposit Successful";
            case "WALLET_WITHDRAWAL" -> "TradePulseAI – Wallet Withdrawal Successful";
            case "STOCK_PURCHASED"   -> "TradePulseAI – Order Completed";
            case "STOCK_SOLD"        -> "TradePulseAI – Sell Order Settled";
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

                        Welcome to TradePulseAI! Your account has been successfully created.
                        You can now log in, add funds to your wallet, and start trading.

                        Happy trading!
                        — The TradePulseAI Team
                        """.formatted(fullName);
            }
            case "WALLET_DEPOSIT" -> {
                String transactionId = getString(data, "transactionId", "N/A");
                String amount  = getString(data, "amount", "0.00");
                String balance = getString(data, "newBalance", "0.00");
                yield """
                        Hi,

                        Your deposit of $%s has been successfully processed.
                        Transaction ID : %s
                        New Balance    : $%s

                        — The TradePulseAI Team
                        """.formatted(amount, transactionId, balance);
            }
            case "WALLET_WITHDRAWAL" -> {
                String transactionId = getString(data, "transactionId", "N/A");
                String amount  = getString(data, "amount", "0.00");
                String balance = getString(data, "newBalance", "0.00");
                yield """
                        Hi,

                        Your withdrawal of $%s has been successfully processed.
                        Transaction ID : %s
                        New Balance    : $%s

                        — The TradePulseAI Team
                        """.formatted(amount, transactionId, balance);
            }
            case "STOCK_PURCHASED" -> {
                String orderId = getString(data, "orderId", "N/A");
                String symbol   = getString(data, "symbol", getString(data, "stockId", "N/A"));
                String quantity = getString(data, "quantity", "0");
                String price    = getString(data, "price", "0.00");
                String total    = getString(data, "total", "0.00");
                yield """
                        Hi,

                        Your stock purchase order has been completed successfully.
                        Order ID  : %s
                        Stock     : %s
                        Quantity  : %s %s
                        Price     : $%s per share
                        Total     : $%s

                        Your portfolio has been updated.

                        — The TradePulseAI Team
                        """.formatted(orderId, symbol, quantity, shareUnit(quantity), price, total);
            }
            case "STOCK_SOLD" -> {
                String symbol   = getString(data, "symbol", "N/A");
                String quantity = getString(data, "quantity", "0");
                String price    = getString(data, "price", "0.00");
                String total    = getString(data, "total", "0.00");
                yield """
                        Hi,

                        Your sell order has been settled successfully.
                        Stock    : %s
                        Quantity : %s %s
                        Price    : $%s per share
                        Total    : $%s credited to your wallet

                        — The TradePulseAI Team
                        """.formatted(symbol, quantity, price, total);
            }
            default -> "A new activity has been recorded on your TradePulseAI account.";
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
}

