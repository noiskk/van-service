package com.bank.service;

import com.bank.entity.van_db;
import com.bank.repository.VANRepository;
import com.card.payment.common.enums.AccountStatus;
import com.card.payment.common.enums.AccountType;
import com.card.payment.common.enums.TransferStatus;
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
 * TransferService 단위 테스트
 * 요구사항: 11.7, 11.8, 13.1
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TransferService 테스트")
class TransferServiceTest {
    
    @Mock
    private VANRepository accountRepository;
    
    @Mock
    private TransferRepository transferRepository;
    
    @Mock
    private TransactionRepository transactionRepository;
    
    @InjectMocks
    private TransferService transferService;
    
    private van_db fromAccount;
    private van_db toAccount;
    
    @BeforeEach
    void setUp() {
        // 출금 계좌 생성
        fromAccount = van_db.builder()
                .id(1L)
                .accountNumber("1111111111")
                .bankCode("001")
                .accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.ACTIVE)
                .balance(new BigDecimal("500000.00"))
                .minimumBalance(new BigDecimal("10000.00"))
                .customerId("CUST001")
                .version(0L)
                .build();
        
        // 입금 계좌 생성
        toAccount = van_db.builder()
                .id(2L)
                .accountNumber("2222222222")
                .bankCode("001")
                .accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.ACTIVE)
                .balance(new BigDecimal("100000.00"))
                .minimumBalance(new BigDecimal("0.00"))
                .customerId("CUST002")
                .version(0L)
                .build();
    }
    
    @Test
    @DisplayName("출금 계좌 검증 - 성공")
    void validateFromAccount_Success() {
        // given
        String accountNumber = "1111111111";
        BigDecimal amount = new BigDecimal("100000.00");
        
        when(accountRepository.findByAccountNumber(accountNumber))
                .thenReturn(Optional.of(fromAccount));
        
        // when
        TransferService.AccountValidationResult result = 
                transferService.validateFromAccount(accountNumber, amount);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
        
        verify(accountRepository).findByAccountNumber(accountNumber);
    }
    
    @Test
    @DisplayName("출금 계좌 검증 - 계좌 번호 null")
    void validateFromAccount_NullAccountNumber() {
        // when
        TransferService.AccountValidationResult result = 
                transferService.validateFromAccount(null, new BigDecimal("10000"));
        
        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("출금 계좌 번호는 필수입니다");
    }
    
    @Test
    @DisplayName("출금 계좌 검증 - 금액 0 이하")
    void validateFromAccount_InvalidAmount() {
        // when
        TransferService.AccountValidationResult result = 
                transferService.validateFromAccount("1111111111", BigDecimal.ZERO);
        
        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("이체 금액은 0보다 커야 합니다");
    }
    
    @Test
    @DisplayName("출금 계좌 검증 - 계좌 없음")
    void validateFromAccount_AccountNotFound() {
        // given
        String accountNumber = "1111111111";
        BigDecimal amount = new BigDecimal("100000.00");
        
        when(accountRepository.findByAccountNumber(accountNumber))
                .thenReturn(Optional.empty());
        
        // when
        TransferService.AccountValidationResult result = 
                transferService.validateFromAccount(accountNumber, amount);
        
        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("출금 계좌를 찾을 수 없습니다");
    }
    
    @Test
    @DisplayName("출금 계좌 검증 - 계좌 상태 비정상")
    void validateFromAccount_InactiveAccount() {
        // given
        String accountNumber = "1111111111";
        BigDecimal amount = new BigDecimal("100000.00");
        
        van_db suspendedAccount = van_db.builder()
                .accountNumber(accountNumber)
                .accountStatus(AccountStatus.SUSPENDED)
                .balance(new BigDecimal("500000.00"))
                .minimumBalance(new BigDecimal("10000.00"))
                .build();
        
        when(accountRepository.findByAccountNumber(accountNumber))
                .thenReturn(Optional.of(suspendedAccount));
        
        // when
        TransferService.AccountValidationResult result = 
                transferService.validateFromAccount(accountNumber, amount);
        
        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).contains("계좌 상태가 정상이 아닙니다");
    }
    
    @Test
    @DisplayName("출금 계좌 검증 - 잔액 부족")
    void validateFromAccount_InsufficientBalance() {
        // given
        String accountNumber = "1111111111";
        BigDecimal amount = new BigDecimal("500000.00"); // 출금 가능 금액(490000) 초과
        
        when(accountRepository.findByAccountNumber(accountNumber))
                .thenReturn(Optional.of(fromAccount));
        
        // when
        TransferService.AccountValidationResult result = 
                transferService.validateFromAccount(accountNumber, amount);
        
        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("출금 계좌의 잔액이 부족합니다");
    }
    
    @Test
    @DisplayName("입금 계좌 검증 - 성공")
    void validateToAccount_Success() {
        // given
        String accountNumber = "2222222222";
        
        when(accountRepository.findByAccountNumber(accountNumber))
                .thenReturn(Optional.of(toAccount));
        
        // when
        TransferService.AccountValidationResult result = 
                transferService.validateToAccount(accountNumber);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrorMessage()).isNull();
        
        verify(accountRepository).findByAccountNumber(accountNumber);
    }
    
    @Test
    @DisplayName("입금 계좌 검증 - 계좌 번호 null")
    void validateToAccount_NullAccountNumber() {
        // when
        TransferService.AccountValidationResult result = 
                transferService.validateToAccount(null);
        
        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("입금 계좌 번호는 필수입니다");
    }
    
    @Test
    @DisplayName("입금 계좌 검증 - 계좌 없음")
    void validateToAccount_AccountNotFound() {
        // given
        String accountNumber = "2222222222";
        
        when(accountRepository.findByAccountNumber(accountNumber))
                .thenReturn(Optional.empty());
        
        // when
        TransferService.AccountValidationResult result = 
                transferService.validateToAccount(accountNumber);
        
        // then
        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrorMessage()).isEqualTo("입금 계좌를 찾을 수 없습니다");
    }
    
    @Test
    @DisplayName("이체 처리 - 성공")
    void processTransfer_Success() {
        // given
        String fromAccountNumber = "1111111111";
        String toAccountNumber = "2222222222";
        BigDecimal amount = new BigDecimal("100000.00");
        String settlementId = "SETTLE001";
        
        when(accountRepository.findByAccountNumber(fromAccountNumber))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(toAccountNumber))
                .thenReturn(Optional.of(toAccount));
        when(accountRepository.findByAccountNumberWithLock(fromAccountNumber))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumberWithLock(toAccountNumber))
                .thenReturn(Optional.of(toAccount));
        when(accountRepository.save(any(van_db.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        TransferService.TransferResult result = 
                transferService.processTransfer(fromAccountNumber, toAccountNumber, amount, settlementId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getTransferId()).isNotNull();
        assertThat(result.getTransferId()).startsWith("TRF-");
        assertThat(result.getFromAccount()).isEqualTo(fromAccountNumber);
        assertThat(result.getToAccount()).isEqualTo(toAccountNumber);
        assertThat(result.getAmount()).isEqualByComparingTo(amount);
        assertThat(result.getFailureReason()).isNull();
        
        verify(accountRepository, times(2)).findByAccountNumber(anyString());
        verify(accountRepository, times(2)).findByAccountNumberWithLock(anyString());
        verify(accountRepository, times(2)).save(any(van_db.class));
        verify(transactionRepository, times(2)).save(any(Transaction.class));
        verify(transferRepository).save(any(Transfer.class));
    }
    
    @Test
    @DisplayName("이체 처리 - 출금 계좌 검증 실패")
    void processTransfer_FromAccountValidationFailed() {
        // given
        String fromAccountNumber = "1111111111";
        String toAccountNumber = "2222222222";
        BigDecimal amount = new BigDecimal("100000.00");
        String settlementId = "SETTLE001";
        
        when(accountRepository.findByAccountNumber(fromAccountNumber))
                .thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        TransferService.TransferResult result = 
                transferService.processTransfer(fromAccountNumber, toAccountNumber, amount, settlementId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getTransferId()).isNotNull();
        assertThat(result.getFailureReason()).isEqualTo("출금 계좌를 찾을 수 없습니다");
        
        verify(transferRepository).save(argThat(transfer -> 
                transfer.getStatus() == TransferStatus.FAILED &&
                transfer.getFailureReason().equals("출금 계좌를 찾을 수 없습니다")
        ));
    }
    
    @Test
    @DisplayName("이체 처리 - 입금 계좌 검증 실패")
    void processTransfer_ToAccountValidationFailed() {
        // given
        String fromAccountNumber = "1111111111";
        String toAccountNumber = "2222222222";
        BigDecimal amount = new BigDecimal("100000.00");
        String settlementId = "SETTLE001";
        
        when(accountRepository.findByAccountNumber(fromAccountNumber))
                .thenReturn(Optional.of(fromAccount));
        when(accountRepository.findByAccountNumber(toAccountNumber))
                .thenReturn(Optional.empty());
        when(transferRepository.save(any(Transfer.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // when
        TransferService.TransferResult result = 
                transferService.processTransfer(fromAccountNumber, toAccountNumber, amount, settlementId);
        
        // then
        assertThat(result).isNotNull();
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReason()).isEqualTo("입금 계좌를 찾을 수 없습니다");
        
        verify(transferRepository).save(argThat(transfer -> 
                transfer.getStatus() == TransferStatus.FAILED
        ));
    }
    
    @Test
    @DisplayName("이체 고유 번호 생성")
    void generateTransferId() {
        // when
        String transferId = transferService.generateTransferId();
        
        // then
        assertThat(transferId).isNotNull();
        assertThat(transferId).startsWith("TRF-");
        assertThat(transferId).hasSize(24); // "TRF-" + 20자리
    }
}
