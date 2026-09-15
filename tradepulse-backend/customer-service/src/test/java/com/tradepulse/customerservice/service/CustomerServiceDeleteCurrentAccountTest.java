package com.tradepulse.customerservice.service;

import com.tradepulse.customerservice.client.AccountChecklistClient;
import com.tradepulse.customerservice.client.AuthServiceClient;
import com.tradepulse.customerservice.exception.AccountDeletionPrecheckFailedException;
import com.tradepulse.customerservice.kafka.kafkaProducer;
import com.tradepulse.customerservice.model.Customer;
import com.tradepulse.customerservice.repository.CustomerRepository;
import com.tradepulse.customerservice.repository.WatchlistItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerServiceDeleteCurrentAccountTest {

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AuthServiceClient authServiceClient;

    @Mock
    private AccountChecklistClient accountChecklistClient;

    @Mock
    private WatchlistItemRepository watchlistItemRepository;

    @Mock
    private kafkaProducer kafkaProducer;

    @InjectMocks
    private CustomerService customerService;

    @Test
    void deleteCurrentAccount_whenChecklistHasBlockers_throwsConflictAndSkipsDelete() {
        Long userId = 101L;
        Customer customer = new Customer();
        customer.setUserId(userId);

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));
        when(accountChecklistClient.fetchChecklistStatus(userId))
                .thenReturn(new AccountChecklistClient.ChecklistStatus(BigDecimal.ONE, 0, 1));

        assertThrows(AccountDeletionPrecheckFailedException.class, () -> customerService.deleteCurrentAccount(userId));

        verify(watchlistItemRepository, never()).deleteByIdUserId(userId);
        verify(customerRepository, never()).delete(customer);
        verify(authServiceClient, never()).deleteUserById(userId);
        verify(kafkaProducer, never()).sendAccountDeletedEvent(userId);
    }

    @Test
    void deleteCurrentAccount_whenChecklistIsClear_deletesCustomerAuthAndPublishesEvent() {
        Long userId = 202L;
        Customer customer = new Customer();
        customer.setUserId(userId);

        when(customerRepository.findByUserId(userId)).thenReturn(Optional.of(customer));
        when(accountChecklistClient.fetchChecklistStatus(userId))
                .thenReturn(new AccountChecklistClient.ChecklistStatus(BigDecimal.ZERO, 0, 0));

        assertDoesNotThrow(() -> customerService.deleteCurrentAccount(userId));

        verify(watchlistItemRepository).deleteByIdUserId(userId);
        verify(customerRepository).delete(customer);
        verify(authServiceClient).deleteUserById(userId);
        verify(kafkaProducer).sendAccountDeletedEvent(userId);
    }

    @Test
    void deleteCurrentAccount_whenCustomerProfileIsMissing_deletesAuthAndPublishesEvent() {
        Long userId = 303L;
        when(customerRepository.findByUserId(userId)).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> customerService.deleteCurrentAccount(userId));

        verify(accountChecklistClient, never()).fetchChecklistStatus(userId);
        verify(watchlistItemRepository, never()).deleteByIdUserId(userId);
        verify(customerRepository, never()).deleteByUserId(userId);
        verify(authServiceClient).deleteUserById(userId);
        verify(kafkaProducer).sendAccountDeletedEvent(userId);
    }
}

