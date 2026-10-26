INSERT INTO policy_box_rule (area_min_py, area_max_py, boxes_min, boxes_max, boxes_avg, note, is_active)
SELECT v.*
FROM (VALUES
(5, 7, 10, 14, 12, '초소형 원룸/오피스텔', TRUE),
(8, 12, 12, 24, 18, '원룸/소형 오피스텔', TRUE),
(13, 16, 18, 30, 24, '큰 원룸/1.5룸', TRUE),
(17, 19, 30, 45, 36, '소형 투룸', TRUE),
(20, 24, 40, 50, 45, '표준 투룸', TRUE),
(25, 29, 45, 60, 52, '소형 3룸', TRUE),
(30, 34, 55, 70, 62, '국민평형', TRUE),
(35, 39, 60, 85, 72, '중대형', TRUE),
(40, 44, 70, 90, 80, '대형', TRUE),
(45, 50, 90, 120, 100, '초대형', TRUE)
) AS v(area_min_py, area_max_py, boxes_min, boxes_max, boxes_avg, note, is_active)
WHERE NOT EXISTS (
    SELECT 1 FROM policy_box_rule p
    WHERE p.area_min_py = v.area_min_py AND p.area_max_py = v.area_max_py
);
