-- Un défi est une étape de la salle : la salle naît en INVITED et porte la partie d'origine d'une revanche et la
-- prochaine relance de l'invitation. Les défis (agrégat, saga, projection) disparaissent.
DROP TABLE public.challenge_entry;

ALTER TABLE public.room_entry ADD COLUMN source_game_id character varying(255);
ALTER TABLE public.room_entry ADD COLUMN remindable_at timestamp without time zone;

DELETE FROM public.association_value_entry WHERE saga_type LIKE '%ChallengeSaga';
DELETE FROM public.saga_entry WHERE saga_type LIKE '%ChallengeSaga';
DELETE FROM public.token_entry WHERE processor_name IN ('challenge-saga', 'challenge-projection', 'user-block-challenges');
