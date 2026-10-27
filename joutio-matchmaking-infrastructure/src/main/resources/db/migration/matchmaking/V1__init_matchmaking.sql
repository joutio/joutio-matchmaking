-- Joutio matchmaking — baseline schema (squashed)
-- Generated from a fresh PostgreSQL 16 database after applying the legacy migrations.

--
-- PostgreSQL database dump
--


-- Dumped from database version 16.13
-- Dumped by pg_dump version 16.13

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: challenge_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.challenge_entry (
    challenge_id character varying(255) NOT NULL,
    topic_id character varying(255) NOT NULL,
    challenger_id character varying(255) NOT NULL,
    opponent_id character varying(255) NOT NULL,
    game_id character varying(255),
    room_id character varying(255),
    status character varying(20) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    resolved_at timestamp without time zone
);


--
-- Name: matchmaking_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.matchmaking_entry (
    matchmaking_id character varying(255) NOT NULL,
    player_id character varying(255) NOT NULL,
    topic_id character varying(255) NOT NULL,
    rating integer NOT NULL,
    languages character varying(64) NOT NULL,
    opponent_id character varying(255),
    room_id character varying(255),
    vs_bot boolean DEFAULT false NOT NULL,
    status character varying(20) NOT NULL,
    claimed_by character varying(255),
    created_at timestamp without time zone NOT NULL,
    deadline_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: room_entry; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.room_entry (
    room_id character varying(255) NOT NULL,
    topic_id character varying(255) NOT NULL,
    initiator_id character varying(255) NOT NULL,
    opponent_id character varying(255),
    participant_id character varying(255),
    game_id character varying(255),
    vs_bot boolean DEFAULT false NOT NULL,
    status character varying(30) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    expires_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL,
    initiator_present boolean DEFAULT false NOT NULL,
    participant_present boolean DEFAULT false NOT NULL
);


--
-- Name: challenge_entry challenge_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.challenge_entry
    ADD CONSTRAINT challenge_entry_pkey PRIMARY KEY (challenge_id);


--
-- Name: matchmaking_entry matchmaking_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.matchmaking_entry
    ADD CONSTRAINT matchmaking_entry_pkey PRIMARY KEY (matchmaking_id);


--
-- Name: room_entry room_entry_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.room_entry
    ADD CONSTRAINT room_entry_pkey PRIMARY KEY (room_id);


--
-- Name: idx_challenge_challenger; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_challenge_challenger ON public.challenge_entry USING btree (challenger_id);


--
-- Name: idx_challenge_opponent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_challenge_opponent ON public.challenge_entry USING btree (opponent_id);


--
-- Name: idx_challenge_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_challenge_status ON public.challenge_entry USING btree (status);


--
-- Name: idx_matchmaking_player; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_matchmaking_player ON public.matchmaking_entry USING btree (player_id);


--
-- Name: idx_matchmaking_pool_rating; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_matchmaking_pool_rating ON public.matchmaking_entry USING btree (topic_id, status, rating, created_at);


--
-- Name: idx_room_initiator; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_room_initiator ON public.room_entry USING btree (initiator_id);


--
-- Name: idx_room_opponent; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_room_opponent ON public.room_entry USING btree (opponent_id);


--
-- Name: idx_room_participant; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_room_participant ON public.room_entry USING btree (participant_id);


--
-- Name: idx_room_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_room_status ON public.room_entry USING btree (status);


--
-- PostgreSQL database dump complete
--


