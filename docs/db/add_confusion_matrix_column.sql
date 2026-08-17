ALTER TABLE ml_models ADD COLUMN `confusion_matrix` JSON COMMENT '混淆矩阵 {tp, fp, fn, tn}' AFTER `feature_importance`;
