package com.tradepulseai.paymentservice.service;

import com.tradepulseai.paymentservice.kafka.NotificationKafkaProducer;
import com.tradepulseai.paymentservice.model.Wallet;
import com.tradepulseai.paymentservice.model.WalletTransaction;
import com.tradepulseai.paymentservice.repository.WalletRepository;
import com.tradepulseai.paymentservice.repository.WalletTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class WalletService {

    private static final Logger log = LoggerFactory.getLogger(WalletService.class);

    private static final String TYPE_DEPOSIT    = "DEPOSIT";
    private static final String TYPE_WITHDRAWAL = "WITHDRAWAL";
    private static final String TYPE_PURCHASE   = "PURCHASE";
    private static final String TYPE_REFUND     = "REFUND";
    private static final String TYPE_SELL_CREDIT = "SELL_CREDIT";

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;
    private final NotificationKafkaProducer notificationKafkaProducer;

    public WalletService(WalletRepository walletRepository,
                         WalletTransactionRepository walletTransactionRepository,
                         NotificationKafkaProducer notificationKafkaProducer) {
        this.walletRepository = walletRepository;
        this.walletTransactionRepository = walletTransactionRepository;
        this.notificationKafkaProducer = notificationKafkaProducer;
    }

    @Transactional
    public Wallet getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId).orElseGet(() -> {
            Wallet wallet = new Wallet();
            wallet.setUserId(userId);
            wallet.setBalance(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
            Wallet saved = walletRepository.save(wallet);
            log.info("Created new wallet for userId={}", userId);
            return saved;
        });
    }

    @Transactional
    public Wallet deposit(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be greater than zero.");
        }

        Wallet wallet = getOrCreateWallet(userId);
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal newBalance = wallet.getBalance().add(scaled);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        Long transactionId = recordTransaction(wallet.getWalletId(), TYPE_DEPOSIT, scaled, newBalance);
        log.info("Deposited {} to walletId={}, newBalance={}", scaled, wallet.getWalletId(), newBalance);
        notificationKafkaProducer.publishWalletDeposit(userId, transactionId, scaled, newBalance);
        return wallet;
    }

    @Transactional
    public Wallet withdraw(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be greater than zero.");
        }

        Wallet wallet = getOrCreateWallet(userId);
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);

        if (wallet.getBalance().compareTo(scaled) < 0) {
            throw new IllegalStateException("Insufficient wallet balance for withdrawal.");
        }

        BigDecimal newBalance = wallet.getBalance().subtract(scaled);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        Long transactionId = recordTransaction(wallet.getWalletId(), TYPE_WITHDRAWAL, scaled, newBalance);
        log.info("Withdrew {} from walletId={}, newBalance={}", scaled, wallet.getWalletId(), newBalance);
        notificationKafkaProducer.publishWalletWithdrawal(userId, transactionId, scaled, newBalance);
        return wallet;
    }

    @Transactional
    public Wallet deductForPurchase(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Purchase amount must be greater than zero.");
        }

        Wallet wallet = getOrCreateWallet(userId);
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);

        if (wallet.getBalance().compareTo(scaled) < 0) {
            throw new IllegalStateException(
                    "Insufficient wallet balance. Available: $" + wallet.getBalance().toPlainString()
                    + ", Required: $" + scaled.toPlainString());
        }

        BigDecimal newBalance = wallet.getBalance().subtract(scaled);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        recordTransaction(wallet.getWalletId(), TYPE_PURCHASE, scaled, newBalance);
        log.info("Purchase deduction of {} from walletId={}, newBalance={}",
                scaled, wallet.getWalletId(), newBalance);
        return wallet;
    }

    @Transactional
    public Wallet refundPurchase(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Refund amount must be greater than zero.");
        }

        Wallet wallet = getOrCreateWallet(userId);
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal newBalance = wallet.getBalance().add(scaled);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        recordTransaction(wallet.getWalletId(), TYPE_REFUND, scaled, newBalance);
        log.info("Refund of {} credited to walletId={}, newBalance={}",
                scaled, wallet.getWalletId(), newBalance);
        return wallet;
    }

    @Transactional
    public Wallet creditForSell(Long userId, BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Sell credit amount must be greater than zero.");
        }

        Wallet wallet = getOrCreateWallet(userId);
        BigDecimal scaled = amount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal newBalance = wallet.getBalance().add(scaled);
        wallet.setBalance(newBalance);
        walletRepository.save(wallet);

        recordTransaction(wallet.getWalletId(), TYPE_SELL_CREDIT, scaled, newBalance);
        log.info("Sell credit of {} applied to walletId={}, newBalance={}",
                scaled, wallet.getWalletId(), newBalance);
        return wallet;
    }

    @Transactional
    public List<WalletTransaction> getTransactions(Long userId) {
        Wallet wallet = getOrCreateWallet(userId);
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getWalletId());
    }

    @Transactional
    public Page<WalletTransaction> getTransactionsPage(Long userId, int page, int size) {
        Wallet wallet = getOrCreateWallet(userId);
        return walletTransactionRepository.findByWalletIdOrderByCreatedAtDesc(wallet.getWalletId(), PageRequest.of(page, size));
    }

    private String recordTransaction(Long walletId, String type, BigDecimal amount, BigDecimal balanceAfter) {
        WalletTransaction tx = new WalletTransaction();
        tx.setWalletId(walletId);
        tx.setTransactionType(type);
        tx.setAmount(amount);
        tx.setBalanceAfter(balanceAfter);
        WalletTransaction saved = walletTransactionRepository.save(tx);
        return saved.getTransactionId();
    }
}

