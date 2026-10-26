INSERT INTO policy_truck_spec (truck_type, name, body_type, inner_width_cm, inner_length_cm, inner_height_cm, cbm_geom, is_active)
VALUES
('T1', '1톤', 'CARGO', 160, 270, 200, 6, TRUE),
('T2_5', '2.5톤', 'TOP', 190, 430, 180, 14, TRUE),
('T5', '5톤', 'TOP', 230, 620, 230, 30, TRUE)
ON CONFLICT (truck_type) DO NOTHING;
