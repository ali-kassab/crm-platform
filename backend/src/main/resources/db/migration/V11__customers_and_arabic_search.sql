-- Customers (D10): individual or company, phone-first; Arabic-aware search.

ALTER TABLE contacts
    ADD COLUMN kind varchar(10) NOT NULL DEFAULT 'INDIVIDUAL',
    ADD CONSTRAINT ck_contacts_kind CHECK (kind IN ('INDIVIDUAL', 'COMPANY'));

ALTER TABLE contacts ALTER COLUMN email DROP NOT NULL;      -- many customers only have a phone
ALTER TABLE contacts ALTER COLUMN first_name DROP NOT NULL; -- a company may have no contact person
ALTER TABLE contacts ALTER COLUMN last_name DROP NOT NULL;  -- single names are common

ALTER TABLE contacts
    ADD CONSTRAINT ck_contacts_kind_name CHECK (
        (kind = 'INDIVIDUAL' AND first_name IS NOT NULL)
        OR (kind = 'COMPANY' AND company IS NOT NULL)),
    ADD CONSTRAINT ck_contacts_reachable CHECK (
        num_nonnulls(email, phone, mobile, work_phone) >= 1);
-- ux_contacts_email (workspace_id, email) WHERE deleted_at IS NULL still applies; NULL emails never collide.

-- Search normalisation shared by every list search (mirrored in Java by SearchText.normalize):
-- lower-case; drop Arabic diacritics (tashkeel, U+064B–U+065F, U+0670) and tatweel (U+0640);
-- unify alef forms أ إ آ ٱ → ا, alef maqsura ى → ي, teh marbuta ة → ه.
CREATE FUNCTION app_search_norm(value text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT AS $$
    SELECT translate(
               lower(regexp_replace(value, '[ً-ٰٟـ]', '', 'g')),
               'أإآٱىة',
               'ااااي' || 'ه')
$$;
