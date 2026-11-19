package com.joutio.matchmaking.domain.model;

import java.util.Map;

/**
 * Contenu localisé d'une question tirée (texte + réponses A-D).
 */
public record DrawnQuestionContent(
        String text,
        Map<DrawnQuestionChoice, String> answers,
        /** Description de l'image dans cette langue (lecteurs d'écran) ; {@code null} si absente. */
        String imageAlt
) {
}
