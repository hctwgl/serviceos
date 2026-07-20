-- M383：允许关闭已发布 Revision 的 effective_to（仅该字段），以支持不可变版本链的生效切换。
-- 禁止修改 manifest/document/digest/version_no 等发布内容。

CREATE OR REPLACE FUNCTION cfg_reject_published_fulfillment_revision_mutation()
RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.revision_status = 'PUBLISHED' THEN
            RAISE EXCEPTION 'published project fulfillment revision is immutable';
        END IF;
        RETURN OLD;
    END IF;

    IF OLD.revision_status = 'PUBLISHED' THEN
        IF NEW.revision_status = OLD.revision_status
           AND NEW.version_no = OLD.version_no
           AND NEW.document_json IS NOT DISTINCT FROM OLD.document_json
           AND NEW.manifest_json IS NOT DISTINCT FROM OLD.manifest_json
           AND NEW.content_digest IS NOT DISTINCT FROM OLD.content_digest
           AND NEW.source_bundle_id IS NOT DISTINCT FROM OLD.source_bundle_id
           AND NEW.workflow_asset_version_id IS NOT DISTINCT FROM OLD.workflow_asset_version_id
           AND NEW.effective_from IS NOT DISTINCT FROM OLD.effective_from
           AND NEW.published_by IS NOT DISTINCT FROM OLD.published_by
           AND NEW.published_at IS NOT DISTINCT FROM OLD.published_at
           AND NEW.supersedes_revision_id IS NOT DISTINCT FROM OLD.supersedes_revision_id
           AND NEW.effective_to IS DISTINCT FROM OLD.effective_to
        THEN
            -- 仅关闭生效窗口，内容仍不可变。
            RETURN NEW;
        END IF;
        RAISE EXCEPTION 'published project fulfillment revision is immutable';
    END IF;
    RETURN NEW;
END;
$$;
