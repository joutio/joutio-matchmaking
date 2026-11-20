-- Relance d'un défi envoyé : prochaine relance possible (10 min après l'envoi, puis après chaque relance).
ALTER TABLE public.challenge_entry ADD COLUMN remindable_at timestamp without time zone;
UPDATE public.challenge_entry SET remindable_at = created_at + interval '10 minutes';
ALTER TABLE public.challenge_entry ALTER COLUMN remindable_at SET NOT NULL;
