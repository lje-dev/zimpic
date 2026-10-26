INSERT INTO policy_distance_fee_rule (truck_type, base_km, unit_km, per_unit_amount, is_active)
VALUES
('T1', 20, 10, 20000, TRUE),
('T2_5', 20, 10, 25000, TRUE),
('T5', 20, 10, 30000, TRUE),
('T6', 20, 10, 30000, TRUE),
('T7_5', 20, 10, 40000, TRUE),
('T10', 20, 10, 50000, TRUE)
ON CONFLICT (truck_type) DO NOTHING;
