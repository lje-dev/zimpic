INSERT INTO policy_stairs_fee_rule (floor_from, floor_to, per_floor_amount, is_active)
VALUES
(2, 19, 15000, TRUE),
(20, 99, 35000, TRUE)
ON CONFLICT (floor_from, floor_to) DO NOTHING;
