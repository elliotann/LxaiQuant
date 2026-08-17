ALTER TABLE ml_models ADD COLUMN `accuracy_trend` JSON COMMENT '近30天每日准确率趋势 JSON [{date: \"2026-04-01\", accuracy: 0.85}, ...]' AFTER `confusion_matrix`;
