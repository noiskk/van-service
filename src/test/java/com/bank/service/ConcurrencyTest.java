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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * 동시성 테스트
 * 동일 계좌에 대한 동시 출금 요청 처리 테스트
 * 요구사항: 12.3, 13.1
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("동시성 테스트")
class ConcurrencyTest {
    
    @Autowired
    private PaymentGatwayService accountService;
    
    @Autowired
    private VANRepository accountRepository;
    
    @Autowired
    private CardAccountMappingRepository cardAccountMappingRepository;
    
    @Autowired
    private TransactionRepository transactionRepository;
    
    private van_db testAccount;
    private CardAccountMapping testMapping;
    
    @BeforeEach
    @Transactional
    void setUp() {
        // 기존 데이터 정리
        transactionRepository.deleteAll();
        cardAccountMappingRepository.deleteAll();
        accountRepository.deleteAll();
        
        // 테스트용 계좌 생성
        testAccount = van_db.builder()
                .accountNumber("1234567890123456") // 20자 이내로 수정
                .bankCode("001")
                .accountType(AccountType.CHECKING)
                .accountStatus(AccountStatus.ACTIVE)
                .balance(new BigDecimal("100000.00"))
                .minimumBalance(new BigDecimal("0.00"))
                .customerId("CUST-CONCURRENT")
                .build();
        testAccount = accountRepository.save(testAccount);
        
        // 테스트용 카드-계좌 매핑 생성
        testMapping = CardAccountMapping.builder()
                .cardNumber("1234567812345678") // 19자 이내로 수정
                .accountNumber(testAccount.getAccountNumber())
                .cardType(CardType.DEBIT)
                .status(MappingStatus.ACTIVE)
                .build();
        testMapping = cardAccountMappingRepository.save(testMapping);
    }
    
    @Test
    @DisplayName("동일 계좌 동시 출금 - 비관적 락으로 순차 처리")
    void concurrentDebit_PessimisticLock() throws InterruptedException {
        // given
        int threadCount = 10;
        BigDecimal debitAmount = new BigDecimal("10000.00");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();
        
        // when
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    PaymentGatwayService.DebitResult result = accountService.processDebit(
                            testMapping.getCardNumber(),
                            debitAmount,
                            "REF-" + index
                    );
                    
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 모든 스레드가 완료될 때까지 대기 (최대 30초)
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        
        // then
        assertThat(completed).isTrue();
        
        // 최종 계좌 잔액 확인
        van_db finalAccount = accountRepository.findByAccountNumber(testAccount.getAccountNumber())
                .orElseThrow();
        
        // 성공한 출금 건수 * 출금 금액 = 차감된 총 금액
        BigDecimal expectedBalance = new BigDecimal("100000.00")
                .subtract(debitAmount.multiply(new BigDecimal(successCount.get())));
        
        assertThat(finalAccount.getBalance()).isEqualByComparingTo(expectedBalance);
        
        // 성공 + 실패 = 전체 요청 수
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);
        
        // 성공한 건수는 10건 (100000 / 10000 = 10)
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failureCount.get()).isEqualTo(0);
        
        System.out.println("=== 동시성 테스트 결과 ===");
        System.out.println("총 요청 수: " + threadCount);
        System.out.println("성공 건수: " + successCount.get());
        System.out.println("실패 건수: " + failureCount.get());
        System.out.println("최종 잔액: " + finalAccount.getBalance());
        System.out.println("예상 잔액: " + expectedBalance);
    }
    
    @Test
    @DisplayName("동일 계좌 동시 출금 - 잔액 부족으로 일부 실패")
    void concurrentDebit_InsufficientBalance() throws InterruptedException {
        // given
        int threadCount = 15; // 100000 / 10000 = 10건만 성공 가능
        BigDecimal debitAmount = new BigDecimal("10000.00");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        
        // when
        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    PaymentGatwayService.DebitResult result = accountService.processDebit(
                            testMapping.getCardNumber(),
                            debitAmount,
                            "REF-" + index
                    );
                    
                    if (result.isSuccess()) {
                        successCount.incrementAndGet();
                    }
                } catch (IllegalStateException e) {
                    // 잔액 부족 예외는 정상적인 실패
                    if (e.getMessage().contains("출금 가능 금액이 부족합니다")) {
                        failureCount.incrementAndGet();
                    } else {
                        throw e;
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 모든 스레드가 완료될 때까지 대기
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        
        // then
        assertThat(completed).isTrue();
        
        // 최종 계좌 잔액 확인
        van_db finalAccount = accountRepository.findByAccountNumber(testAccount.getAccountNumber())
                .orElseThrow();
        
        // 성공 + 실패 = 전체 요청 수
        assertThat(successCount.get() + failureCount.get()).isEqualTo(threadCount);
        
        // 성공한 건수는 10건
        assertThat(successCount.get()).isEqualTo(10);
        
        // 실패한 건수는 5건
        assertThat(failureCount.get()).isEqualTo(5);
        
        // 최종 잔액은 0원
        assertThat(finalAccount.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        
        System.out.println("=== 동시성 테스트 결과 (잔액 부족) ===");
        System.out.println("총 요청 수: " + threadCount);
        System.out.println("성공 건수: " + successCount.get());
        System.out.println("실패 건수: " + failureCount.get());
        System.out.println("최종 잔액: " + finalAccount.getBalance());
    }
    
    @Test
    @DisplayName("동일 계좌 동시 잔액 조회 - 비관적 락으로 일관성 보장")
    void concurrentBalanceCheck_PessimisticLock() throws InterruptedException {
        // given
        int threadCount = 20;
        BigDecimal requestAmount = new BigDecimal("50000.00");
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        List<PaymentGatwayService.BalanceCheckResult> results = new CopyOnWriteArrayList<>();
        
        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    PaymentGatwayService.BalanceCheckResult result = accountService.checkBalance(
                            testAccount.getAccountNumber(),
                            requestAmount
                    );
                    results.add(result);
                } finally {
                    latch.countDown();
                }
            });
        }
        
        // 모든 스레드가 완료될 때까지 대기
        boolean completed = latch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();
        
        // then
        assertThat(completed).isTrue();
        assertThat(results).hasSize(threadCount);
        
        // 모든 조회 결과가 동일한 잔액을 반환해야 함
        BigDecimal expectedBalance = new BigDecimal("100000.00");
        for (PaymentGatwayService.BalanceCheckResult result : results) {
            assertThat(result.getBalance()).isEqualByComparingTo(expectedBalance);
            assertThat(result.isCanWithdraw()).isTrue();
        }
        
        System.out.println("=== 동시 잔액 조회 테스트 결과 ===");
        System.out.println("총 조회 수: " + threadCount);
        System.out.println("조회된 잔액: " + results.get(0).getBalance());
    }
}
