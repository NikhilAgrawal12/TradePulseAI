package com.tradepulseai.custservice.service;

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

    public PortfolioService(
            PortfolioHoldingRepository portfolioHoldingRepository,
            PortfolioTransactionRepository portfolioTransactionRepository
    ) {
        this.portfolioHoldingRepository = portfolioHoldingRepository;
        this.portfolioTransactionRepository = portfolioTransactionRepository;
    }

    @Transactional(readOnly = true)
    public PortfolioResponseDTO getPortfolio(String userEmail) {
        List<PortfolioHolding> holdings = portfolioHoldingRepository.findByUserEmailOrderByUpdatedAtDesc(userEmail);
        List<PortfolioTransaction> transactions = portfolioTransactionRepository.findByUserEmailOrderByExecutedAtDesc(userEmail);

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
        for (PortfolioFillItemRequestDTO item : request.getItems()) {
            PortfolioHolding holding = portfolioHoldingRepository.findByUserEmailAndStockId(userEmail, item.getStockId())
                    .orElseGet(() -> PortfolioMapper.newHolding(userEmail, item));

            if (holding.getId() != null) {
                mergeBuyIntoHolding(holding, item);
            }

            portfolioHoldingRepository.save(holding);
            portfolioTransactionRepository.save(PortfolioMapper.toBuyTransaction(userEmail, item));
        }

        return getPortfolio(userEmail);
    }

    @Transactional
    public PortfolioResponseDTO sellPosition(String userEmail, String stockId, SellPortfolioItemRequestDTO request) {
        PortfolioHolding holding = portfolioHoldingRepository.findByUserEmailAndStockId(userEmail, stockId)
                .orElseThrow(() -> new IllegalArgumentException("Portfolio holding not found for stockId: " + stockId));

        if (request.getQuantity() > holding.getTotalQuantity()) {
            throw new IllegalArgumentException("Sell quantity cannot be greater than owned quantity.");
        }

        BigDecimal sellAmount = PortfolioMapper.calculateGrossAmount(request.getPrice(), request.getQuantity());
        BigDecimal avgCost = PortfolioMapper.scaleMoney(holding.getAverageBuyPrice());
        BigDecimal costBasis = PortfolioMapper.calculateGrossAmount(avgCost, request.getQuantity());
        BigDecimal realizedPnl = PortfolioMapper.scaleMoney(sellAmount.subtract(costBasis));

        portfolioTransactionRepository.save(
                PortfolioMapper.toSellTransaction(
                        userEmail,
                        holding.getStockId(),
                        holding.getSymbol(),
                        request.getQuantity(),
                        request.getPrice(),
                        realizedPnl
                )
        );

        int remainingQuantity = holding.getTotalQuantity() - request.getQuantity();
        if (remainingQuantity == 0) {
            portfolioHoldingRepository.delete(holding);
        } else {
            holding.setTotalQuantity(remainingQuantity);
            holding.setCurrentMarketPrice(PortfolioMapper.scaleMoney(request.getPrice()));
            holding.setInvestedAmount(PortfolioMapper.calculateGrossAmount(avgCost, remainingQuantity));
            holding.setRealizedPnl(PortfolioMapper.scaleMoney(holding.getRealizedPnl().add(realizedPnl)));
            portfolioHoldingRepository.save(holding);
        }

        return getPortfolio(userEmail);
    }

    private void mergeBuyIntoHolding(PortfolioHolding holding, PortfolioFillItemRequestDTO item) {
        int updatedQuantity = holding.getTotalQuantity() + item.getQuantity();
        BigDecimal updatedInvestedAmount = PortfolioMapper.scaleMoney(
                holding.getInvestedAmount().add(PortfolioMapper.calculateGrossAmount(item.getPrice(), item.getQuantity()))
        );

        BigDecimal updatedAverageBuyPrice = updatedInvestedAmount
                .divide(BigDecimal.valueOf(updatedQuantity), 6, RoundingMode.HALF_UP);

        holding.setSymbol(item.getSymbol());
        holding.setTotalQuantity(updatedQuantity);
        holding.setInvestedAmount(updatedInvestedAmount);
        holding.setAverageBuyPrice(PortfolioMapper.scaleMoney(updatedAverageBuyPrice));
        holding.setCurrentMarketPrice(PortfolioMapper.scaleMoney(item.getPrice()));
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

