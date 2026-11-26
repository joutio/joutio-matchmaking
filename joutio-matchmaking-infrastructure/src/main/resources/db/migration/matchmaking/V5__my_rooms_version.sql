CREATE TABLE public.my_rooms_version (
    player_id character varying(255) PRIMARY KEY,
    version bigint NOT NULL
);

COMMENT ON TABLE public.my_rooms_version IS 'Version des salles d''un joueur (vue vivante GetMyRooms)';
