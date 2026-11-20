-- Origine d'une salle (salon privé, défi, revanche, appariement, bot) : sections de l'écran Défis.
ALTER TABLE public.room_entry ADD COLUMN origin character varying(20);
UPDATE public.room_entry SET origin = CASE
    WHEN vs_bot THEN 'BOT'
    WHEN opponent_id IS NULL THEN 'SHARED'
    ELSE 'CHALLENGE'
END;
ALTER TABLE public.room_entry ALTER COLUMN origin SET NOT NULL;
