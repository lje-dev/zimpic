INSERT INTO policy_base_price (move_type, truck_type, base_amount, included_workers, note, is_active)
VALUES
('GENERAL', 'T1', 120000, 1, '', TRUE),
('GENERAL', 'T2_5', 200000, 2, '', TRUE),
('GENERAL', 'T5', 350000, 3, '5톤 이상 포장이사 자동 전환', FALSE),
('PACKING', 'T1', 500000, 1, '', TRUE),
('PACKING', 'T2_5', 1000000, 3, '남2+여1', TRUE),
('PACKING', 'T5', 1300000, 4, '남3+여1', TRUE),
('PACKING', 'T6', 1500000, 5, '남4+여1', TRUE),
('PACKING', 'T7_5', 1900000, 6, '남4+여2', TRUE),
('PACKING', 'T10', 2500000, 7, '남5+여2', TRUE)
ON CONFLICT (move_type, truck_type) DO NOTHING;
