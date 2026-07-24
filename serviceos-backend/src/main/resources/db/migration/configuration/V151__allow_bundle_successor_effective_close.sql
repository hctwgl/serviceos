-- 配置包发布后仍保持内容不可变；版本切换只允许把一个开放区间关闭一次。
-- 同一事务随后插入后继版本，历史工单继续通过 bundle_id + manifest_digest 冻结旧事实。
CREATE OR REPLACE FUNCTION cfg_reject_published_bundle_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND OLD.effective_until IS NULL
       AND NEW.effective_until IS NOT NULL
       AND NEW.effective_until > OLD.effective_from
       AND (to_jsonb(NEW) - 'effective_until') =
           (to_jsonb(OLD) - 'effective_until') THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'published configuration is immutable';
END;
$$;

DROP TRIGGER trg_cfg_bundle_immutable ON cfg_configuration_bundle;

CREATE TRIGGER trg_cfg_bundle_immutable
    BEFORE UPDATE OR DELETE ON cfg_configuration_bundle
    FOR EACH ROW EXECUTE FUNCTION cfg_reject_published_bundle_mutation();
