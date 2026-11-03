UPDATE course_resource
SET media_type = CASE
    WHEN LOWER(original_name) LIKE '%.mp4' OR LOWER(original_name) LIKE '%.m4v' THEN 'video/mp4'
    WHEN LOWER(original_name) LIKE '%.webm' THEN 'video/webm'
    WHEN LOWER(original_name) LIKE '%.jpg' OR LOWER(original_name) LIKE '%.jpeg' THEN 'image/jpeg'
    WHEN LOWER(original_name) LIKE '%.png' THEN 'image/png'
    WHEN LOWER(original_name) LIKE '%.gif' THEN 'image/gif'
    WHEN LOWER(original_name) LIKE '%.webp' THEN 'image/webp'
    WHEN LOWER(original_name) LIKE '%.pdf' THEN 'application/pdf'
    ELSE media_type
END
WHERE media_type IS NULL OR media_type='application/octet-stream';
