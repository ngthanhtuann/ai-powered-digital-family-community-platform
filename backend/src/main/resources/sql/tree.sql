WITH RECURSIVE TreeCTE AS (
    SELECT id, family_id, first_name, last_name, birth_date, is_deceased, address, avatar_media_id, 0 as lvl 
    FROM person 
    WHERE id = :rootId AND deleted_at IS NULL 
    UNION ALL 
    SELECT p.id, p.family_id, p.first_name, p.last_name, p.birth_date, p.is_deceased, p.address, p.avatar_media_id, t.lvl + 1 
    FROM person p 
    JOIN parent_child pc ON p.id = pc.child_id 
    JOIN TreeCTE t ON pc.parent_id = t.id 
    WHERE p.deleted_at IS NULL AND pc.deleted_at IS NULL AND t.lvl < :depth
) 
SELECT DISTINCT id, first_name, last_name, birth_date, is_deceased, address, avatar_media_id, lvl 
FROM TreeCTE

