package com.tradepulseai.custservice.service;

import com.tradepulseai.custservice.client.AuthServiceClient;
import com.tradepulseai.custservice.dto.PortfolioFillItemRequestDTO;
import com.tradepulseai.custservice.dto.PortfolioHoldingResponseDTO;
import com.tradepulseai.custservice.dto.PortfolioResponseDTO;
import com.tradepulseai.custservice.dto.PortfolioSummaryResponseDTO;
import com.tradepulseai.custservice.dto.PortfolioTransactionResponseDTO;
import com.tradepulseai.custservice.dto.RecordPortfolioOrderRequestDTO;
import com.tradepulseai.custservice.dto.SellPortfolioItemRequestDTO;
import com.tradepulseai.custservice.mapper.PortfolioMapper;
import com.tradepulseai.custservice.model.PortfolioHolding;
import com.tradepulseai.custservice.model.PortfolioTransaction;
import com.tradepulseai.custservice.repository.PortfolioHoldingRepository;
import com.tradepulseai.custservice.repository.PortfolioTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class PortfolioService {

    private final PortfolioHoldingRepository portfolioHoldingRepository;
    private final PortfolioTransactionRepository portfolioTransactionRepository;
    private final AuthServiceClient authServiceClient;

    public PortfolioService(
            PortfolioHoldingRepository portfolioHoldingRepository,
            PortfolioTransactionRepository portfolioTransactionRepository,
            AuthServiceClient authServiceClient
    ) {
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.portfolioTransactionRepository = portfolioTransactionRepository;
        this.authServiceClient = authServiceClient;
    }

    @Transactional(readOnly = true)
    public PortfolioResponseDTO getPortfolio(String userEmail) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        List<PortfolioHolding> holdings = portfolioHoldingRepository.findByIdUserIdOrderByUpdatedAtDesc(userId);
        List<PortfolioTransaction> transactions = portfolioTransactionRepository.findByUserIdOrderByExecutedAtDesc(userId);

        List<PortfolioHoldingResponseDTO> holdingResponses = holdings.stream()
                .map(PortfolioMapper::toHoldingResponse)
                .toList();

        List<PortfolioTransactionResponseDTO> transactionResponses = transactions.stream()
                .map(PortfolioMapper::toTransactionResponse)
                .toList();

        PortfolioResponseDTO response = new PortfolioResponseDTO();
        response.setSummary(toSummary(holdingResponses));
        response.setHoldings(holdingResponses);
        response.setTransactions(transactionResponses);
        return response;
    }

    @Transactional
    public PortfolioResponseDTO recordCompletedOrder(String userEmail, RecordPortfolioOrderRequestDTO request) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        for (PortfolioFillItemRequestDTO item : request.getItems()) {
            Long stockId = parseStockId(item.getStockId());
            PortfolioHolding holding = portfolioHoldingRepository.findByIdUserIdAndIdStockId(userId, stockId)
                    .orElseGet(() -> PortfolioMapper.newHolding(userId, item));

            if (holding.getId() != null && holding.getTotalQuantity() != null) {
                mergeBuyIntoHolding(holding, item);
            }

            portfolioHoldingRepository.save(holding);
            portfolioTransactionRepository.save(PortfolioMapper.toBuyTransaction(userId, item));
        }

        return getPortfolio(userEmail);
    }

    @Transactional
    public PortfolioResponseDTO sellPosition(String userEmail, String stockId, SellPortfolioItemRequestDTO request) {
        Long userId = authServiceClient.getUserByEmail(userEmail).userId();
        Long parsedStockId = parseStockId(stockId);

        PortfolioHolding holding = portfolioHoldingRepository.findByIdUserIdAndIdStockId(userId, parsedStockId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio holding not found for stockId: " + stockId));

        BigDecimal requestedQty = PortfolioMapper.scaleQuantity(BigDecimal.valueOf(request.getQuantity()));
        if (requestedQty.compareTo(holding.getTotalQuantity()) > 0) {
            throw new IllegalArgumentException("Sell quantity cannot be greater than owned quantity.");
        }

        portfolioTransactionRepository.save(
                PortfolioMapper.toSellTransaction(
                        userId,
                        holding.getId().getStockId(),
                        request.getQuantity(),
                        request.getPrice()
                )
        );

        BigDecimal remainingQuantity = holding.getTotalQuantity().subtract(requestedQty);
        if (remainingQuantity.compareTo(BigDecimal.ZERO) == 0) {
            portfolioHoldingRepository.delete(holding);
        } else {
            holding.setTotalQuantity(PortfolioMapper.scaleQuantity(remainingQuantity));
            portfolioHoldingRepository.save(holding);
        }

        return getPortfolio(userEmail);
    }

    private void mergeBuyIntoHolding(PortfolioHolding holding, PortfolioFillItemRequestDTO item) {
        BigDecimal updatedQuantity = holding.getTotalQuantity().add(BigDecimal.valueOf(item.getQuantity()));
        holding.setTotalQuantity(PortfolioMapper.scaleQuantity(updatedQuantity));
    }

    private Long parseStockId(String stockId) {
        try {
            return Long.parseLong(stockId);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid stockId format: " + stockId);
        }
    }

    private PortfolioSummaryResponseDTO toSummary(List<PortfolioHoldingResponseDTO> holdings) {
        BigDecimal totalInvested = holdings.stream()
                .map(PortfolioHoldingResponseDTO::getInvestedValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalMarketValue = holdings.stream()
                .map(PortfolioHoldingResponseDTO::getMarketValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalUnrealized = holdings.stream()
                .map(PortfolioHoldingResponseDTO::getUnrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRealized = holdings.stream()
                .map(PortfolioHoldingResponseDTO::getRealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int totalQuantity = holdings.stream()
                .mapToInt(PortfolioHoldingResponseDTO::getQuantity)
                .sum();

        BigDecimal totalUnrealizedPercent = BigDecimal.ZERO;
        if (totalInvested.compareTo(BigDecimal.ZERO) > 0) {
            totalUnrealizedPercent = totalUnrealized
                    .divide(totalInvested, 6, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        PortfolioSummaryResponseDTO summary = new PortfolioSummaryResponseDTO();
        summary.setTotalPositions(holdings.size());
        summary.setTotalQuantity(totalQuantity);
        summary.setTotalInvestedValue(PortfolioMapper.scaleMoney(totalInvested));
        summary.setTotalMarketValue(PortfolioMapper.scaleMoney(totalMarketValue));
        summary.setTotalUnrealizedPnl(PortfolioMapper.scaleMoney(totalUnrealized));
        summary.setTotalUnrealizedPnlPercent(totalUnrealizedPercent.setScale(2, RoundingMode.HALF_UP));
        summary.setTotalRealizedPnl(PortfolioMapper.scaleMoney(totalRealized));
        return summary;
    }
}
