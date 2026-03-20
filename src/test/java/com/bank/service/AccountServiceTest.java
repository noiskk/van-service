package com.bank.service;

import com.bank.entity.van_db;
import com.bank.repository.VANRepository;
import com.card.payment.common.enums.AccountStatus;
import com.card.payment.common.enums.AccountType;
import com.card.payment.common.enums.CardType;
import com.card.payment.common.enums.MappingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AccountService 단위 테스트
 * 요구사항: 4.2, 4.7, 12.3, 13.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService 테스트")
class AccountServiceTest {
    
    @Mock
    private VANRepository accountRepository;
    
    @Mock
    private CardAccountMappingRepository cardAccountMappingRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @InjectMocks
    private PaymentGatwayService accountService;
    
    private van_db testAccount;
    private CardAccountMapping testMapping;
    
    @BeforeEach
    void setUp() {
        // 테스트용 계좌 생성
        testAccount = van_db.builder()
                .id(1L)
                .accountNumber("1234567890")
                .bankCode("001")
                .accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.ACTIVE)
                .balance(new BigDecimal("100000.00"))
                .minimumBalance(new BigDecimal("10000.00"))
                .customerId("CUST001")
                .version(0L)
                .build();
        
        // 테스트용 카드-계좌 매핑 생성
        testMapping = CardAccountMapping.builder()
                .id(1L)
                .cardNumber("1234567812345678")
                .accountNumber("1234567890")
                .cardType(CardType.DEBIT)
                .status(MappingStatus.ACTIVE)
                .build();
    }
    
    @Test
    @DisplayName("카드 번호로 계좌 조회 - 성공")
    void findAccountByCardNumber_Success() {
        // given
        String cardNumber = "1234567812345678";
        when(cardAccountMappingRepository.findByCardNumberAndStatus(cardNumber, MappingStatus.ACTIVE))
                .thenReturn(Optional.of(testMapping));
        when(accountRepository.findByAccountNumber(testMapping.getAccountNumber()))
                .thenReturn(Optional.of(testAccount));
        
        // when
        van_db result = accountService.findAccountByCardNumber(cardNumber);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getAccountNumber()).isEqualTo("1234567890");
        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("100000.00"));
        
        verify(cardAccountMappingRepository).findByCardNumberAndStatus(cardNumber, MappingStatus.ACTIVE);
        verify(accountRepository).findByAccountNumber(testMapping.getAccountNumber());
    }
    
    @Test
    @DisplayName("카드 번호로 계좌 조회 - 카드 번호 null")
    void findAccountByCardNumber_NullCardNumber() {
        // when & then
        assertThatThrownBy(() -> accountService.findAccountByCardNumber(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("카드 번호는 필수입니다");
    }
    
    @Test
    @DisplayName("카드 번호로 계좌 조회 - 매핑 정보 없음")
    void findAccountByCardNumber_MappingNotFound() {
        // given
        String cardNumber = "1234567812345678";
        when(cardAccountMappingRepository.findByCardNumberAndStatus(cardNumber, MappingStatus.ACTIVE))
                .thenReturn(Optional.empty());
        
        // when & then
        assertThatThrownBy(() -> accountService.findAccountByCardNumber(cardNumber))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("활성 상태의 카드-계좌 매핑을 찾을 수 없습니다");
    }
    
    @Test
    @DisplayName("잔액 조회 - 성공 (출금 가능)")
    void checkBalance_Success_CanWithdraw() {
        // given
        String accountNumber = "1234567890";
        BigDecimal requestAmount = new BigDecimal("50000.00");
        
        when(accountRepository.findByAccountNumberWithLock(accountNumber))
                .thenReturn(Optional.of(testAccount));
        
        // when
        PaymentGatwayService.BalanceCheckResult result = accountService.checkBalance(accountNumber, requestAmount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.getAccountNumber()).isEqualTo(accountNumber);
        assertThat(result.getBalance()).isEqualByComparingTo(new BigDecimal("100000.00"));
        assertThat(result.getMinimumBalance()).isEqualByComparingTo(new BigDecimal("10000.00"));
        assertThat(result.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("90000.00"));
        assertThat(result.getRequestAmount()).isEqualByComparingTo(requestAmount);
        assertThat(result.isCanWithdraw()).isTrue();
        
        verify(accountRepository).findByAccountNumberWithLock(accountNumber);
    }
    
    @Test
    @DisplayName("잔액 조회 - 성공 (출금 불가)")
    void checkBalance_Success_CannotWithdraw() {
        // given
        String accountNumber = "1234567890";
        BigDecimal requestAmount = new BigDecimal("95000.00");
        
        when(accountRepository.findByAccountNumberWithLock(accountNumber))
                .thenReturn(Optional.of(testAccount));
        
        // when
        PaymentGatwayService.BalanceCheckResult result = accountService.checkBalance(accountNumber, requestAmount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.isCanWithdraw()).isFalse();
        assertThat(result.getAvailableBalance()).isEqualByComparingTo(new BigDecimal("90000.00"));
    }
    
    @Test
    @DisplayName("잔액 조회 - 계좌 번호 null")
    void checkBalance_NullAccountNumber() {
        // when & then
        assertThatThrownBy(() -> accountService.checkBalance(null, new BigDecimal("10000")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("계좌 번호는 필수입니다");
    }
    
    @Test
    @DisplayName("잔액 조회 - 요청 금액 0 이하")
    void checkBalance_InvalidAmount() {
        // when & then
        assertThatThrownBy(() -> accountService.checkBalance("1234567890", BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("요청 금액은 0보다 커야 합니다");
    }
    
    @Test
    @DisplayName("잔액 조회 - 계좌 상태 비정상")
    void checkBalance_InactiveAccount() {
        // given
        String accountNumber = "1234567890";
        van_db suspendedAccount = van_db.builder()
                .accountNumber(accountNumber)
                .accountStatus(AccountStatus.SUSPENDED)
                .balance(new BigDecimal("100000.00"))
                .minimumBalance(new BigDecimal("10000.00"))
                .build();
        
        when(accountRepository.findByAccountNumberWithLock(accountNumber))
                .thenReturn(Optional.of(suspendedAccount));
        
        // when & then
        assertThatThrownBy(() -> accountService.checkBalance(accountNumber, new BigDecimal("10000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌 상태가 정상이 아닙니다: SUSPENDED");
    }
    
    @Test
    @DisplayName("출금 가능 금액 계산 - 성공")
    void calculateAvailableBalance_Success() {
        // when
        BigDecimal availableBalance = accountService.calculateAvailableBalance(testAccount);
        
        // then
        assertThat(availableBalance).isEqualByComparingTo(new BigDecimal("90000.00"));
    }
    
    @Test
    @DisplayName("출금 가능 금액 계산 - 계좌 정보 null")
    void calculateAvailableBalance_NullAccount() {
        // when & then
        assertThatThrownBy(() -> accountService.calculateAvailableBalance(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("계좌 정보는 필수입니다");
    }
    
    @Test
    @DisplayName("출금 처리 - 성공")
    void processDebit_Success() {
        // given
        String cardNumber = "1234567812345678";
        BigDecimal amount = new BigDecimal("50000.00");
        String referenceId = "APPR12345678";
        
        when(cardAccountMappingRepository.findByCardNumberAndStatus(cardNumber, MappingStatus.ACTIVE))
                .thenReturn(Optional.of(testMapping));
        when(accountRepository.findByAccountNumberWithLock(testMapping.getAccountNumber()))
                .thenReturn(Optional.of(testAccount));
        when(accountRepository.save(any(van_db.class)))
                .thenReturn(testAccount);
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        PaymentGatwayService.DebitResult result = accountService.processDebit(cardNumber, amount, referenceId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getAccountNumber()).isEqualTo("1234567890");
        assertThat(result.getAmount()).isEqualByComparingTo(amount);
        assertThat(result.getTransactionId()).isNotNull();
        assertThat(result.getTransactionId()).startsWith("TXN-");
        
        verify(cardAccountMappingRepository).findByCardNumberAndStatus(cardNumber, MappingStatus.ACTIVE);
        verify(accountRepository).findByAccountNumberWithLock(testMapping.getAccountNumber());
        verify(accountRepository).save(any(van_db.class));
        verify(transactionRepository).save(any(Transaction.class));
    }
    
    @Test
    @DisplayName("출금 처리 - 카드 번호 null")
    void processDebit_NullCardNumber() {
        // when & then
        assertThatThrownBy(() -> accountService.processDebit(null, new BigDecimal("10000"), "REF001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("카드 번호는 필수입니다");
    }
    
    @Test
    @DisplayName("출금 처리 - 출금 금액 0 이하")
    void processDebit_InvalidAmount() {
        // when & then
        assertThatThrownBy(() -> accountService.processDebit("1234567812345678", BigDecimal.ZERO, "REF001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("출금 금액은 0보다 커야 합니다");
    }
    
    @Test
    @DisplayName("출금 처리 - 잔액 부족")
    void processDebit_InsufficientBalance() {
        // given
        String cardNumber = "1234567812345678";
        BigDecimal amount = new BigDecimal("95000.00"); // 출금 가능 금액(90000) 초과
        String referenceId = "APPR12345678";
        
        when(cardAccountMappingRepository.findByCardNumberAndStatus(cardNumber, MappingStatus.ACTIVE))
                .thenReturn(Optional.of(testMapping));
        when(accountRepository.findByAccountNumberWithLock(testMapping.getAccountNumber()))
                .thenReturn(Optional.of(testAccount));
        
        // when & then
        assertThatThrownBy(() -> accountService.processDebit(cardNumber, amount, referenceId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("출금 가능 금액이 부족합니다");
    }
    
    @Test
    @DisplayName("출금 처리 - 계좌 상태 비정상")
    void processDebit_InactiveAccount() {
        // given
        String cardNumber = "1234567812345678";
        BigDecimal amount = new BigDecimal("50000.00");
        String referenceId = "APPR12345678";
        
        van_db suspendedAccount = van_db.builder()
                .accountNumber("1234567890")
                .accountStatus(AccountStatus.SUSPENDED)
                .balance(new BigDecimal("100000.00"))
                .minimumBalance(new BigDecimal("10000.00"))
                .build();
        
        when(cardAccountMappingRepository.findByCardNumberAndStatus(cardNumber, MappingStatus.ACTIVE))
                .thenReturn(Optional.of(testMapping));
        when(accountRepository.findByAccountNumberWithLock(testMapping.getAccountNumber()))
                .thenReturn(Optional.of(suspendedAccount));
        
        // when & then
        assertThatThrownBy(() -> accountService.processDebit(cardNumber, amount, referenceId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("계좌 상태가 정상이 아닙니다: SUSPENDED");
    }
}
