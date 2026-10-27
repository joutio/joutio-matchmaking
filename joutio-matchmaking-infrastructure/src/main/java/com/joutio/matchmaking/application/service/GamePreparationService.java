package com.joutio.matchmaking.application.service;

import com.joutio.game.domain.command.GameCommand;
import com.joutio.game.domain.model.BotDifficulty;
import com.joutio.game.domain.model.GamePlayerType;
import com.joutio.game.domain.model.GameQuestion;
import com.joutio.game.domain.model.GameQuestionChoice;
import com.joutio.game.domain.model.GameQuestionContent;
import com.joutio.game.domain.model.PlayerProgressSnapshot;
import com.joutio.matchmaking.domain.model.DrawnQuestion;
import com.joutio.matchmaking.domain.model.DrawnQuestionChoice;
import com.joutio.matchmaking.domain.model.DrawnQuestionContent;
import com.joutio.matchmaking.domain.model.RoomPlayer;
import com.joutio.microservice.core.domain.constant.JoutioConstants;
import com.joutio.microservice.core.domain.model.i18n.Language;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Assemblage de la commande de création de partie (contrat joutio-game) à partir du modèle local
 * de la salle : mapping local → {@code game.GameQuestion}, résolution des joueurs (bot compris) et
 * de leurs progressions. La saga salle orchestre, elle ne mappe pas ; le domaine matchmaking ne
 * dépend jamais du domaine game.
 */
@Service
public class GamePreparationService {

    public GameCommand.CreateGameCommand createGameCommand(String gameId,
                                                           String topicId,
                                                           RoomPlayer initiator,
                                                           RoomPlayer participant,
                                                           boolean vsBot,
                                                           String botDifficulty,
                                                           List<DrawnQuestion> questions) {
        BotDifficulty difficulty = vsBot ? parseBotDifficulty(botDifficulty) : null;
        return new GameCommand.CreateGameCommand(
                gameId,
                topicId,
                initiator.playerId(),
                initiator.playerName(),
                vsBot ? JoutioConstants.SYSTEM_USER_ID : participant.playerId(),
                vsBot ? JoutioConstants.SYSTEM_USER_NAME : participant.playerName(),
                vsBot ? GamePlayerType.BOT : GamePlayerType.HUMAN,
                difficulty,
                new PlayerProgressSnapshot(initiator.rating()),
                vsBot
                        ? PlayerProgressSnapshot.forBot()
                        : new PlayerProgressSnapshot(participant.rating()),
                toGameQuestions(questions));
    }

    // ── Mapping du modèle local vers le domaine game (un *-domain ne dépend jamais d'un autre service) ──

    private static List<GameQuestion> toGameQuestions(List<DrawnQuestion> questions) {
        return questions.stream().map(GamePreparationService::toGameQuestion).toList();
    }

    private static GameQuestion toGameQuestion(DrawnQuestion question) {
        Map<Language, GameQuestionContent> translations = new EnumMap<>(Language.class);
        question.translations().forEach((language, content) ->
                translations.put(language, toGameQuestionContent(content)));
        return new GameQuestion(
                question.questionId(),
                translations,
                question.imageUrl(),
                question.difficulty(),
                toGameQuestionChoice(question.correctAnswer()));
    }

    private static GameQuestionContent toGameQuestionContent(DrawnQuestionContent content) {
        Map<GameQuestionChoice, String> answers = new EnumMap<>(GameQuestionChoice.class);
        content.answers().forEach((choice, text) -> {
            GameQuestionChoice mapped = toGameQuestionChoice(choice);
            if (mapped != null) {
                answers.put(mapped, text);
            }
        });
        return new GameQuestionContent(content.text(), answers);
    }

    private static GameQuestionChoice toGameQuestionChoice(DrawnQuestionChoice choice) {
        return choice == null ? null : GameQuestionChoice.valueOf(choice.name());
    }

    private static BotDifficulty parseBotDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return BotDifficulty.NORMAL;
        }
        try {
            return BotDifficulty.valueOf(difficulty);
        } catch (IllegalArgumentException exception) {
            return BotDifficulty.NORMAL;
        }
    }
}
