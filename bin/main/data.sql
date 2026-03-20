-- Bank Service 테스트 데이터

-- 계좌 데이터 삽입
INSERT INTO accounts (id, account_number, bank_code, account_type, account_status, balance, minimum_balance, customer_id, created_at, version) VALUES
(1, '1000000001', '001', 'CHECKING', 'ACTIVE', 1000000.00, 0.00, 'CUST-001', NOW(), 0),
(2, '2000000001', '001', 'CHECKING', 'ACTIVE', 500000.00, 0.00, 'CUST-002', NOW(), 0),
(3, '3000000001', '001', 'SAVINGS', 'ACTIVE', 2000000.00, 100000.00, 'CUST-003', NOW(), 0),
(4, '4000000001', '001', 'CHECKING', 'ACTIVE', 3000000.00, 0.00, 'CUST-004', NOW(), 0),
(5, '5000000001', '001', 'CHECKING', 'ACTIVE', 100000.00, 0.00, 'CUST-005', NOW(), 0),
(6, '6000000001', '001', 'CHECKING', 'ACTIVE', 500000.00, 0.00, 'CUST-006', NOW(), 0),
(7, '7000000001', '001', 'CHECKING', 'ACTIVE', 200000.00, 0.00, 'CUST-007', NOW(), 0);

-- 카드-계좌 매핑 데이터 삽입
-- Luhn 알고리즘을 통과하는 유효한 테스트 카드 번호 사용
-- Card Authorization Service의 카드 번호와 일치하도록 설정
INSERT INTO card_account_mappings (id, card_number, account_number, card_type, status, created_at) VALUES
(1, '4111111111111111', '1000000001', 'DEBIT', 'ACTIVE', NOW()),
(2, '5555555555554444', '2000000001', 'DEBIT', 'ACTIVE', NOW()),
(3, '378282246310005', '3000000001', 'DEBIT', 'ACTIVE', NOW()),
(4, '6011111111111117', '4000000001', 'DEBIT', 'ACTIVE', NOW()),
(5, '3530111333300000', '5000000001', 'DEBIT', 'ACTIVE', NOW()),
(6, '5105105105105100', '6000000001', 'DEBIT', 'ACTIVE', NOW()),
(7, '4012888888881881', '7000000001', 'DEBIT', 'ACTIVE', NOW());
