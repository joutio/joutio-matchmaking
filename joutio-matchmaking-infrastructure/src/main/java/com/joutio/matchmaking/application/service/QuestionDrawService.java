package com.joutio.matchmaking.application.service;

import com.joutio.game.domain.model.GameRules;
import com.joutio.matchmaking.domain.model.DrawnQuestion;
import com.joutio.matchmaking.domain.model.DrawnQuestionChoice;
import com.joutio.matchmaking.domain.model.DrawnQuestionContent;
import com.joutio.matchmaking.domain.model.DrawnQuestionDifficulty;
import com.joutio.matchmaking.domain.port.out.QuestionDrawPort;
import com.joutio.microservice.core.domain.model.i18n.Language;
import com.joutio.microservice.core.infrastructure.axon.QueryResponseTypes;
import com.joutio.theme.domain.model.Question;
import com.joutio.theme.domain.model.QuestionChoice;
import com.joutio.theme.domain.model.QuestionContent;
import com.joutio.theme.domain.model.QuestionDifficulty;
import com.joutio.theme.domain.query.QuestionQuery;
import org.axonframework.queryhandling.QueryGateway;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static java.util.Objects.isNull;

/**
 * Adaptateur sortant du port {@link QuestionDrawPort} : tirage aléatoire strict sur les langues
 * (joutio-theme), avec **préférences** de tri best-effort — jamais-jouées puis difficulté déduite
 * du niveau des joueurs — et mapping vers le <b>modèle local</b> du domaine matchmaking
 * (un {@code *-domain} ne dépend jamais d'un autre service).
 * <p>
 * Requête <b>non bloquante</b> : jamais de {@code join()} ici — le résultat est renvoyé sous forme
 * de {@link CompletableFuture} et la saga salle le ramène par commande/événement. Appelé par la
 * saga salle (fenêtre d'attente) et non par un handler de commande : l'I/O inter-service ne doit
 * jamais bloquer une action utilisateur.
 */
@Service
public class QuestionDrawService implements QuestionDrawPort {

    private final QueryGateway queryGateway;

    public QuestionDrawService(QueryGateway queryGateway) {
        this.queryGateway = queryGateway;
    }

    @Override
    public CompletableFuture<List<DrawnQuestion>> draw(String topicId, Set<Language> languages,
                                                       DrawnQuestionDifficulty target, Set<String> excludeQuestionIds) {
        if (languages == null || languages.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        Set<QuestionDifficulty> difficulties = target == null
                ? Set.of()
                : Set.of(QuestionDifficulty.valueOf(target.name()));
        return queryGateway.query(
                        new QuestionQuery.GetRandomApprovedQuestionsQuery(
                                topicId,
                                GameRules.TOTAL_ROUNDS,
                                languages,
                                difficulties,
                                excludeQuestionIds == null ? Set.of() : excludeQuestionIds),
                        QueryResponseTypes.multipleInstancesOf(Question.class))
                .thenApply(questions -> questions.stream().map(QuestionDrawService::toDrawnQuestion).toList());
    }

    // ── Mapping theme → modèle local (dupliqué par émetteur : un *-domain ne dépend jamais d'un autre service) ──

    private static DrawnQuestion toDrawnQuestion(Question question) {
        Map<Language, DrawnQuestionContent> translations = new EnumMap<>(Language.class);
        if (question.contents() != null) {
            for (Map.Entry<Language, QuestionContent> content : question.contents().entrySet()) {
                translations.put(content.getKey(), toDrawnQuestionContent(content.getValue()));
            }
        }
        return new DrawnQuestion(
                question.questionId(),
                translations,
                question.imageUrl(),
                question.difficulty() != null ? question.difficulty().name() : null,
                toDrawnQuestionChoice(question.correctAnswer()));
    }

    private static DrawnQuestionContent toDrawnQuestionContent(QuestionContent content) {
        return new DrawnQuestionContent(content.text(), toDrawnQuestionChoices(content.answers()));
    }

    private static DrawnQuestionChoice toDrawnQuestionChoice(QuestionChoice questionChoice) {
        return isNull(questionChoice) ? null : DrawnQuestionChoice.valueOf(questionChoice.name());
    }

    private static Map<DrawnQuestionChoice, String> toDrawnQuestionChoices(Map<QuestionChoice, String> questionChoices) {
        Map<DrawnQuestionChoice, String> drawnChoices = new EnumMap<>(DrawnQuestionChoice.class);
        if (isNull(questionChoices) || questionChoices.isEmpty()) {
            return drawnChoices;
        }
        for (Map.Entry<QuestionChoice, String> entry : questionChoices.entrySet()) {
            DrawnQuestionChoice choice = toDrawnQuestionChoice(entry.getKey());
            if (!isNull(choice)) {
                drawnChoices.put(choice, entry.getValue());
            }
        }
        return drawnChoices;
    }
}
